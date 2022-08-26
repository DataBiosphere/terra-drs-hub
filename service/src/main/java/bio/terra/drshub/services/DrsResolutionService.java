package bio.terra.drshub.services;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.DrsProviderInterface;
import bio.terra.drshub.logging.AuditLogEvent;
import bio.terra.drshub.logging.AuditLogEventType;
import bio.terra.drshub.logging.AuditLogger;
import bio.terra.drshub.models.AnnotatedResourceMetadata;
import bio.terra.drshub.models.DrsHubAuthorization;
import bio.terra.drshub.models.DrsMetadata;
import bio.terra.drshub.models.Fields;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.DrsObject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;

@Service
@Slf4j
public record DrsResolutionService(
    DrsApiFactory drsApiFactory,
    DrsProviderService drsProviderService,
    AuthService authService,
    AuditLogger auditLogger) {

  /**
   * Resolve the Drs Object for the provided uri, including requested fields.
   *
   * @param drsUri uri (but a string) of the object to resolve
   * @param rawRequestedFields requested fields as provided by the user
   * @param bearerToken the user's bearer token
   * @param forceAccessUrl if true, force the fetching of the access url
   * @param ip ip address for audit logging purposes
   * @return All the object info plus some details about the request
   */
  public AnnotatedResourceMetadata resolveDrsObject(
      String drsUri,
      List<String> rawRequestedFields,
      BearerToken bearerToken,
      Boolean forceAccessUrl,
      String ip) {

    var requestedFields = isEmpty(rawRequestedFields) ? Fields.DEFAULT_FIELDS : rawRequestedFields;

    var uriComponents = drsProviderService.getUriComponents(drsUri);
    var provider = drsProviderService.determineDrsProvider(uriComponents);

    log.info(
        "Drs URI '{}' will use provider {}, requested fields {}",
        drsUri,
        provider.getName(),
        String.join(", ", requestedFields));

    var metadata =
        fetchObject(
            provider, requestedFields, uriComponents, drsUri, bearerToken, forceAccessUrl, ip);

    return buildResponseObject(requestedFields, metadata, provider);
  }

  private DrsMetadata fetchObject(
      DrsProvider drsProvider,
      List<String> requestedFields,
      UriComponents uriComponents,
      String drsUri,
      BearerToken bearerToken,
      boolean forceAccessUrl,
      String ip) {

    AuditLogEvent.Builder auditEventBuilder =
        new AuditLogEvent.Builder()
            .dRSUrl(uriComponents.toUriString())
            .providerName(drsProvider.getName())
            .clientIP(Optional.ofNullable(ip));

    final DrsObject drsResponse;
    final List<DrsHubAuthorization> authorizations;

    if (Fields.shouldRequestObjectInfo(requestedFields)) {
      try {
        authorizations =
            authService.buildAuthorizations(
                drsProvider, uriComponents, bearerToken, forceAccessUrl);
        drsResponse = fetchObjectInfo(drsProvider, uriComponents, drsUri, bearerToken);
      } catch (Exception e) {
        auditLogger.logEvent(
            auditEventBuilder.auditLogEventType(AuditLogEventType.DrsResolutionFailed).build());
        throw e;
      }
    } else {
      authorizations = List.of();
      drsResponse = null;
    }

    var drsMetadataBuilder = new DrsMetadata.Builder();
    drsMetadataBuilder.drsResponse(drsResponse);

    var accessMethod = getAccessMethod(drsResponse, drsProvider);
    var accessMethodType = accessMethod.map(AccessMethod::getType).orElse(null);

    if (drsProvider.shouldFetchUserServiceAccount(accessMethodType, requestedFields)) {
      var saKey = authService.fetchUserServiceAccount(drsProvider, bearerToken);
      drsMetadataBuilder.bondSaKey(saKey);
    }

    if (drsResponse != null) {
      getDrsFileName(drsResponse).ifPresent(drsMetadataBuilder::fileName);
      drsMetadataBuilder.localizationPath(getLocalizationPath(drsProvider, drsResponse));

      if (drsProvider.shouldFetchAccessUrl(accessMethodType, requestedFields, forceAccessUrl)) {
        var accessId = accessMethod.map(AccessMethod::getAccessId).orElseThrow();
        try {
          var providerAccessMethod = drsProvider.getAccessMethodByType(accessMethodType);
          auditEventBuilder.authType(providerAccessMethod.getAuth());

          log.info("Requesting URL for {}", uriComponents.toUriString());

          var accessUrl =
              fetchDrsObjectAccessUrl(
                  drsProvider,
                  uriComponents,
                  accessId,
                  accessMethodType,
                  authorizations,
                  auditEventBuilder);
          drsMetadataBuilder.accessUrl(accessUrl);
        } catch (RuntimeException e) {
          auditLogger.logEvent(
              auditEventBuilder.auditLogEventType(AuditLogEventType.DrsResolutionFailed).build());
          if (DrsProviderInterface.shouldFailOnAccessUrlFail(accessMethodType)) {
            throw e;
          } else {
            log.warn("Ignoring error from fetching signed URL", e);
          }
        }
      }
    }

    auditLogger.logEvent(
        auditEventBuilder.auditLogEventType(AuditLogEventType.DrsResolutionSucceeded).build());
    return drsMetadataBuilder.build();
  }

  private DrsObject fetchObjectInfo(
      DrsProvider drsProvider,
      UriComponents uriComponents,
      String drsUri,
      BearerToken bearerToken) {
    var sendMetadataAuth = drsProvider.isMetadataAuth();

    var objectId = getObjectId(uriComponents);
    log.info(
        "Requesting DRS metadata for '{}' with auth required '{}' from host '{}'",
        drsUri,
        sendMetadataAuth,
        uriComponents.getHost());

    var drsApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
    if (sendMetadataAuth) {
      drsApi.setBearerToken(bearerToken.getToken());
    }

    return drsApi.getObject(objectId, null);
  }

  private AccessURL fetchDrsObjectAccessUrl(
      DrsProvider drsProvider,
      UriComponents uriComponents,
      String accessId,
      TypeEnum accessMethodType,
      List<DrsHubAuthorization> drsHubAuthorizations,
      AuditLogEvent.Builder auditLogEventBuilder) {

    var drsApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
    var objectId = getObjectId(uriComponents);

    for (var authorization : drsHubAuthorizations) {
      Optional<Object> auth = authorization.getAuthForAccessMethodType().apply(accessMethodType);
      var accessUrl =
          switch (authorization.drsAuthType()) {
            case NONE -> drsApi.getAccessURL(objectId, accessId);
            case BASICAUTH -> throw new BadRequestException(
                "DRSHub does not support basic username/password authentication at this time.");
            case BEARERAUTH -> {
              drsApi.setBearerToken(
                  auth.map(Objects::toString)
                      .orElseThrow(
                          () ->
                              new BadRequestException(
                                  String.format(
                                      "Fence access token required for %s but is missing. Does user have an account linked in Bond?",
                                      uriComponents.toUriString()))));
              yield drsApi.getAccessURL(objectId, accessId);
            }
            case PASSPORTAUTH -> {
              try {
                yield drsApi.postAccessURL(
                    Map.of("passports", auth.orElse(List.of(""))), objectId, accessId);
              } catch (RestClientException e) {
                log.error(
                    "Passport authorized request failed for {} with error {}",
                    uriComponents.toUriString(),
                    e.getMessage());
                yield null;
              }
            }
          };
      if (accessUrl != null) {
        auditLogEventBuilder.authType(
            drsProvider.getAccessMethodByType(accessMethodType).getAuth());
        return accessUrl;
      }
    }
    return null;
  }

  private String getObjectId(UriComponents uriComponents) {
    // TODO: is there a reason we need query params? it breaks getAccessUrl.
    return uriComponents.getPath();
  }

  private Optional<AccessMethod> getAccessMethod(DrsObject drsResponse, DrsProvider drsProvider) {
    if (!isEmpty(drsResponse)) {
      for (var methodConfig : drsProvider.getAccessMethodConfigs()) {
        var matchingMethod =
            drsResponse.getAccessMethods().stream()
                .filter(m -> methodConfig.getType().getReturnedEquivalent() == m.getType())
                .findFirst();
        if (matchingMethod.isPresent()) {
          return matchingMethod;
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Attempts to return the file name using only the drsResponse.
   *
   * <p>It is possible the name may need to be retrieved from the signed url.
   */
  private Optional<String> getDrsFileName(DrsObject drsResponse) {

    if (!isEmpty(drsResponse.getName())) {
      return Optional.of(drsResponse.getName());
    }

    return Optional.ofNullable(drsResponse.getAccessMethods().get(0).getAccessUrl())
        .map(url -> URI.create(url.getUrl()).getPath())
        .map(path -> path.replaceAll("^.*[\\\\/]", ""));
  }

  private String getLocalizationPath(DrsProvider drsProvider, DrsObject drsResponse) {
    if (drsProvider.useAliasesForLocalizationPath() && !isEmpty(drsResponse.getAliases())) {
      return drsResponse.getAliases().get(0);
    }
    return null;
  }

  private AnnotatedResourceMetadata buildResponseObject(
      List<String> requestedFields, DrsMetadata drsMetadata, DrsProvider drsProvider) {

    return AnnotatedResourceMetadata.builder()
        .requestedFields(requestedFields)
        .drsMetadata(drsMetadata)
        .drsProvider(drsProvider)
        .build();
  }
}
