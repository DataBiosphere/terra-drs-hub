package bio.terra.drshub.services;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.DrsProviderInterface;
import bio.terra.drshub.generated.model.RequestObject.CloudPlatformEnum;
import bio.terra.drshub.logging.AuditLogEvent;
import bio.terra.drshub.logging.AuditLogEventType;
import bio.terra.drshub.logging.AuditLogger;
import bio.terra.drshub.models.AnnotatedResourceMetadata;
import bio.terra.drshub.models.DrsHubAuthorization;
import bio.terra.drshub.models.DrsMetadata;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.util.AccessMethodUtils;
import com.google.common.annotations.VisibleForTesting;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.Authorizations;
import io.github.ga4gh.drs.model.DrsObject;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;

@Service
@Slf4j
public class DrsResolutionService {

  private final DrsApiFactory drsApiFactory;
  private final DrsProviderService drsProviderService;
  private final AuthService authService;
  private final AuditLogger auditLogger;

  @Autowired
  public DrsResolutionService(
      DrsApiFactory drsApiFactory,
      DrsProviderService drsProviderService,
      AuthService authService,
      AuditLogger auditLogger) {
    this.drsApiFactory = drsApiFactory;
    this.drsProviderService = drsProviderService;
    this.authService = authService;
    this.auditLogger = auditLogger;
  }

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
  @Async("asyncExecutor")
  public CompletableFuture<AnnotatedResourceMetadata> resolveDrsObject(
      String drsUri,
      CloudPlatformEnum cloudPlatform,
      List<String> rawRequestedFields,
      BearerToken bearerToken,
      Boolean forceAccessUrl,
      String ip,
      String googleProject) {

    var requestedFields = isEmpty(rawRequestedFields) ? Fields.DEFAULT_FIELDS : rawRequestedFields;

    var uriComponents = drsProviderService.getUriComponents(drsUri);
    var provider = drsProviderService.determineDrsProvider(uriComponents);

    log.info(
        "Drs URI {} will use provider {}, requested fields {}",
        drsUri,
        provider.getName(),
        String.join(", ", requestedFields));

    var metadata =
        fetchObject(
            provider,
            cloudPlatform,
            requestedFields,
            uriComponents,
            drsUri,
            bearerToken,
            forceAccessUrl,
            ip,
            googleProject);

    var response = buildResponseObject(requestedFields, metadata, provider, ip);

    return CompletableFuture.completedFuture(response);
  }

  private DrsMetadata fetchObject(
      DrsProvider drsProvider,
      CloudPlatformEnum cloudPlatform,
      List<String> requestedFields,
      UriComponents uriComponents,
      String drsUri,
      BearerToken bearerToken,
      boolean forceAccessUrl,
      String ip,
      String googleProject) {

    AuditLogEvent.Builder auditEventBuilder =
        new AuditLogEvent.Builder()
            .dRSUrl(uriComponents.toUriString())
            .providerName(drsProvider.getName())
            .clientIP(Optional.ofNullable(ip));

    final DrsObject drsResponse;
    final List<DrsHubAuthorization> authorizations;
    if (Fields.shouldRequestObjectInfo(requestedFields)) {
      try {
        authorizations = authService.buildAuthorizations(drsProvider, uriComponents, bearerToken);
        drsResponse =
            fetchObjectInfo(drsProvider, uriComponents, drsUri, bearerToken, authorizations);
      } catch (Exception e) {
        auditLogger.logEvent(
            auditEventBuilder.auditLogEventType(AuditLogEventType.DrsResolutionFailed).build());
        throw e;
      }
    } else {
      drsResponse = null;
      authorizations = List.of();
    }

    var drsMetadataBuilder = new DrsMetadata.Builder();

    var accessMethod = AccessMethodUtils.getAccessMethod(drsResponse, drsProvider, cloudPlatform);
    var accessMethodType = accessMethod.map(AccessMethod::getType).orElse(null);

    if (drsProvider.shouldFetchUserServiceAccount(accessMethodType, requestedFields)) {
      var saKey = authService.fetchUserServiceAccount(drsProvider, bearerToken);
      drsMetadataBuilder.bondSaKey(saKey);
    }

    if (drsResponse != null) {
      drsMetadataBuilder.drsResponse(drsResponse);
      setDrsResponseValues(
          drsMetadataBuilder,
          drsResponse,
          drsProvider,
          accessMethod,
          accessMethodType,
          requestedFields,
          uriComponents,
          auditEventBuilder,
          authorizations,
          forceAccessUrl,
          ip,
          googleProject);
    }

    auditLogger.logEvent(
        auditEventBuilder.auditLogEventType(AuditLogEventType.DrsResolutionSucceeded).build());
    return drsMetadataBuilder.build();
  }

  private void setDrsResponseValues(
      DrsMetadata.Builder drsMetadataBuilder,
      DrsObject drsResponse,
      DrsProvider drsProvider,
      Optional<AccessMethod> accessMethod,
      TypeEnum accessMethodType,
      List<String> requestedFields,
      UriComponents uriComponents,
      AuditLogEvent.Builder auditEventBuilder,
      List<DrsHubAuthorization> authorizations,
      boolean forceAccessUrl,
      String ip,
      String googleProject) {

    getDrsFileName(drsResponse).ifPresent(drsMetadataBuilder::fileName);
    drsMetadataBuilder.localizationPath(getLocalizationPath(drsProvider, drsResponse));

    if (drsProvider.shouldFetchAccessUrl(accessMethodType, requestedFields, forceAccessUrl)) {
      var accessId = accessMethod.map(AccessMethod::getAccessId).orElseThrow();
      try {
        log.info("Requesting URL for {}", uriComponents.toUriString());
        var accessUrl =
            fetchDrsObjectAccessUrl(
                drsProvider,
                uriComponents,
                accessId,
                accessMethodType,
                authorizations,
                auditEventBuilder,
                ip,
                googleProject);
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

  @VisibleForTesting
  DrsObject fetchObjectInfo(
      DrsProvider drsProvider,
      UriComponents uriComponents,
      String drsUri,
      BearerToken bearerToken,
      List<DrsHubAuthorization> authorizations) {
    var sendMetadataAuth = drsProvider.isMetadataAuth();

    var objectId = getObjectId(uriComponents);
    String drsRequestLogMessage =
        "Requesting DRS metadata for %s with auth required %s from host %s"
            .formatted(drsUri, sendMetadataAuth, uriComponents.getHost());
    log.info(drsRequestLogMessage);

    var drsApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
    if (sendMetadataAuth) {
      // Currently, no provider needs a fence_token for metadata auth.
      // If that changes, this will need to get updated.
      drsApi.setBearerToken(bearerToken.getToken());
      if (authorizations.stream()
          .anyMatch(a -> a.drsAuthType() == Authorizations.SupportedTypesEnum.PASSPORTAUTH)) {
        try {
          List<String> passports = authService.fetchPassports(bearerToken).orElse(List.of());
          if (!passports.isEmpty()) {
            return drsApi.postObject(Map.of("passports", passports), objectId);
          }
        } catch (Exception ex) {
          // We are catching a general exception to ensure that we fall back to getting the object
          // via bearer token in case of any failure
          log.warn(drsRequestLogMessage + " failed via passport, using bearer token", ex);
        }
      }
    }

    return drsApi.getObject(objectId, null);
  }

  @VisibleForTesting
  AccessURL fetchDrsObjectAccessUrl(
      DrsProvider drsProvider,
      UriComponents uriComponents,
      String accessId,
      TypeEnum accessMethodType,
      List<DrsHubAuthorization> drsHubAuthorizations,
      AuditLogEvent.Builder auditLogEventBuilder,
      String googleProject,
      String ip) {

    var drsApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
    var objectId = getObjectId(uriComponents);
    // TODO: thread IP address through and set like this on 266
    // TODO: ask team if we should do an if drsProvider is TDR or just send it on everything
    if (googleProject != null) {
      drsApi.setHeader("x-user-project", googleProject);
    }
    drsApi.setHeader("x-forwarded-for", ip);

    for (var authorization : drsHubAuthorizations) {
      Optional<List<String>> auth =
          authorization.getAuthForAccessMethodType().apply(accessMethodType);
      var accessUrl =
          switch (authorization.drsAuthType()) {
            case NONE -> drsApi.getAccessURL(objectId, accessId);
            case BASICAUTH -> throw new BadRequestException(
                "DRSHub does not support basic username/password authentication at this time.");
            case BEARERAUTH -> {
              drsApi.setBearerToken(
                  auth.map(l -> l.get(0))
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
                yield auth.map(
                        a -> drsApi.postAccessURL(Map.of("passports", a), objectId, accessId))
                    .orElse(null);
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

  static String getObjectId(UriComponents uriComponents) {
    // TODO: is there a reason we need query params? it breaks getAccessUrl.
    return URLDecoder.decode(
        Optional.ofNullable(uriComponents.getPath()).orElse(""), StandardCharsets.UTF_8);
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
      List<String> requestedFields, DrsMetadata drsMetadata, DrsProvider drsProvider, String ip) {

    return AnnotatedResourceMetadata.builder()
        .requestedFields(requestedFields)
        .drsMetadata(drsMetadata)
        .drsProvider(drsProvider)
        .ip(ip)
        .build();
  }
}
