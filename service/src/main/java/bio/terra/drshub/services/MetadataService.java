package bio.terra.drshub.services;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.DrsProviderInterface;
import bio.terra.drshub.logging.AuditLogEvent;
import bio.terra.drshub.logging.AuditLogEventType;
import bio.terra.drshub.logging.AuditLogger;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.AnnotatedResourceMetadata;
import bio.terra.drshub.models.DrsHubAuthorization;
import bio.terra.drshub.models.DrsMetadata;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.util.AuthUtils;
import com.google.common.annotations.VisibleForTesting;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.Authorizations;
import io.github.ga4gh.drs.model.DrsObject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public record MetadataService(
    BondApiFactory bondApiFactory,
    DrsApiFactory drsApiFactory,
    DrsHubConfig drsHubConfig,
    ExternalCredsApiFactory externalCredsApiFactory,
    AuthUtils authUtils,
    AuditLogger auditLogger) {

  private static final Pattern drsRegex =
      Pattern.compile(
          "(?:dos|drs)://(?:(?<compactId>dg\\.[0-9a-z-]+)|(?<hostname>[^?/]+\\.[^?/]+))[:/](?<suffix>[^?]*)",
          Pattern.CASE_INSENSITIVE);

  public AnnotatedResourceMetadata fetchResourceMetadata(
      String drsUri,
      List<String> rawRequestedFields,
      BearerToken bearerToken,
      Boolean forceAccessUrl,
      String ip) {

    var requestedFields = isEmpty(rawRequestedFields) ? Fields.DEFAULT_FIELDS : rawRequestedFields;

    var uriComponents = getUriComponents(drsUri);
    var provider = determineDrsProvider(uriComponents);

    log.info(
        "Drs URI '{}' will use provider {}, requested fields {}",
        drsUri,
        provider.getName(),
        String.join(", ", requestedFields));

    var metadata =
        fetchMetadata(
            provider, requestedFields, uriComponents, drsUri, bearerToken, forceAccessUrl, ip);

    return buildResponseObject(requestedFields, metadata, provider);
  }

  /**
   * DRS schemes are allowed as of <a
   * href="https://ga4gh.github.io/data-repository-service-schemas/preview/release/drs-1.2.0/docs/">DRS
   * 1.2</a>
   *
   * <p>DOS is still supported as a URI scheme in case there are URIs in that form in the wild
   * however all providers support DRS 1.2. So parsing the URI treats DOS and DRS interchangeably
   * but the resolution is all DRS 1.2
   *
   * <p>Note: GA4GH Compact Identifier based URIs are incompatible with W3C/IETF URIs and the
   * various standard libraries that parse them because they use colons as a delimiter. However,
   * there are some Compact Identifier based URIs that use slashes as a delimiter. This code assumes
   * that if the host part of the URI is of the form dg.[0-9a-z-]+ then it is a Compact Identifier.
   *
   * <p>If you update *any* of the below be sure to link to the supporting docs and update the
   * comments above!
   */
  public UriComponents getUriComponents(String drsUri) {

    var drsRegexMatch = drsRegex.matcher(drsUri);

    if (drsRegexMatch.matches()) {
      var compactIdHost =
          Optional.ofNullable(drsRegexMatch.group("compactId"))
              .map(compactId -> drsHubConfig.getCompactIdHosts().get(compactId.toLowerCase()));
      var hostname = Optional.ofNullable(drsRegexMatch.group("hostname"));
      var dnsHost =
          compactIdHost
              .or(() -> hostname)
              .orElseThrow(
                  () ->
                      new BadRequestException(
                          String.format(
                              "Could not find matching host for compact id [%s].",
                              drsRegexMatch.group("compactId"))));

      return UriComponentsBuilder.newInstance()
          .host(dnsHost)
          .path(drsRegexMatch.group("suffix"))
          .build();
    } else {
      throw new BadRequestException(String.format("[%s] is not a valid DRS URI.", drsUri));
    }
  }

  public DrsProvider determineDrsProvider(UriComponents uriComponents) {
    var host = uriComponents.getHost();
    assert host != null;

    if (host.endsWith("dataguids.org")) {
      throw new BadRequestException(
          "dataguids.org data has moved. See: https://support.terra.bio/hc/en-us/articles/360060681132");
    }

    var providers = drsHubConfig.getDrsProviders();

    return providers.values().stream()
        .filter(p -> host.matches(p.getHostRegex()))
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    String.format(
                        "Could not determine DRS provider for id `%s`",
                        uriComponents.toUriString())));
  }

  @VisibleForTesting
  Optional<Authorizations> fetchDrsAuthorizations(
      DrsProvider drsProvider, UriComponents uriComponents) {
    var drsApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
    var objectId = getObjectId(uriComponents);
    try {
      return Optional.ofNullable(drsApi.optionsObject(objectId));
    } catch (RestClientException ex) {
      log.warn(
          "Failed to get authorizations for {} from OPTIONS endpoint for DRS Provider {}. "
              + "Falling back to configured authorizations",
          objectId,
          drsProvider.getName());
      return Optional.empty();
    }
  }

  private DrsMetadata fetchMetadata(
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

    if (Fields.shouldRequestMetadata(requestedFields)) {
      try {
        authorizations =
            authUtils.buildAuthorizations(drsProvider, uriComponents, bearerToken, forceAccessUrl);
        drsResponse = fetchDrsObject(drsProvider, uriComponents, drsUri, bearerToken);
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
      var bondApi = bondApiFactory.getApi(bearerToken);
      drsMetadataBuilder.bondSaKey(
          bondApi.getLinkSaKey(drsProvider.getBondProvider().orElseThrow().getUriValue()));
    }

    if (drsResponse != null) {
      drsMetadataBuilder.fileName(getDrsFileName(drsResponse));
      drsMetadataBuilder.localizationPath(getLocalizationPath(drsProvider, drsResponse));

      if (drsProvider.shouldFetchAccessUrl(accessMethodType, requestedFields, forceAccessUrl)) {
        var accessId = accessMethod.map(AccessMethod::getAccessId).orElseThrow();
        try {
          var providerAccessMethod = drsProvider.getAccessMethodByType(accessMethodType);
          auditEventBuilder.authType(providerAccessMethod.getAuth());

          log.info("Requesting URL for {}", uriComponents.toUriString());

          var accessUrl =
              getAccessUrl(
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

  private DrsObject fetchDrsObject(
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

  private AccessURL getAccessUrl(
      DrsProvider drsProvider,
      UriComponents uriComponents,
      String accessId,
      TypeEnum accessMethodType,
      List<DrsHubAuthorization> drsHubAuthorizations,
      AuditLogEvent.Builder auditLogEventBuilder) {

    var drsApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
    var objectId = getObjectId(uriComponents);

    for (var authorization : drsHubAuthorizations) {
      Optional<Object> auth = authorization.auths().apply(accessMethodType);
      var foo =
          switch (authorization.authType()) {
            case NONE -> drsApi.getAccessURL(objectId, accessId);
            case BASICAUTH -> {
              auth.map(Object::toString).ifPresent(drsApi::setBearerToken);
              yield drsApi.getAccessURL(objectId, accessId);
            }
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
      if (foo != null) {
        auditLogEventBuilder.authType(AccessUrlAuthEnum.fromDrsAuthType(authorization.authType()));
        return foo;
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
  private String getDrsFileName(DrsObject drsResponse) {

    if (!isEmpty(drsResponse.getName())) {
      return drsResponse.getName();
    }

    var accessURL = drsResponse.getAccessMethods().get(0).getAccessUrl();

    if (accessURL != null) {
      var path = URI.create(accessURL.getUrl()).getPath();
      return path == null ? null : path.replaceAll("^.*[\\\\/]", "");
    }
    return null;
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
