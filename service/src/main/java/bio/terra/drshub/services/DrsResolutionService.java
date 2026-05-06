package bio.terra.drshub.services;

import static io.github.ga4gh.drs.model.Authorizations.SupportedTypesEnum.BEARERAUTH;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.datarepo.api.DataRepositoryServiceApi;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.DRSAccessURL;
import bio.terra.datarepo.model.DRSPassportRequestModel;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.DrsProviderInterface;
import bio.terra.drshub.generated.model.RequestObject.CloudPlatformEnum;
import bio.terra.drshub.generated.model.ServiceName;
import bio.terra.drshub.logging.AuditLogEvent;
import bio.terra.drshub.logging.AuditLogEventType;
import bio.terra.drshub.logging.AuditLogger;
import bio.terra.drshub.models.AnnotatedResourceMetadata;
import bio.terra.drshub.models.DrsApi;
import bio.terra.drshub.models.DrsAuthEnum;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;

@Service
@Slf4j
public class DrsResolutionService {

  private final DrsApiFactory drsApiFactory;
  private final AuthService authService;
  private final AuditLogger auditLogger;
  private final TdrApiFactory tdrApiFactory;
  public static final String TRANSACTION_ID_HEADER_NAME = "X-Transaction-Id";

  @Autowired
  public DrsResolutionService(
      DrsApiFactory drsApiFactory,
      AuthService authService,
      AuditLogger auditLogger,
      TdrApiFactory tdrApiFactory) {
    this.drsApiFactory = drsApiFactory;
    this.authService = authService;
    this.auditLogger = auditLogger;
    this.tdrApiFactory = tdrApiFactory;
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
      Optional<ServiceName> serviceName,
      BearerToken bearerToken,
      Boolean forceAccessUrl,
      String ip,
      String googleProject,
      String transactionId,
      UriComponents uriComponents,
      DrsProvider provider) {

    var requestedFields = isEmpty(rawRequestedFields) ? Fields.DEFAULT_FIELDS : rawRequestedFields;

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
            serviceName,
            uriComponents,
            drsUri,
            bearerToken,
            forceAccessUrl,
            ip,
            googleProject,
            transactionId);

    var response = buildResponseObject(requestedFields, metadata, provider);

    return CompletableFuture.completedFuture(response);
  }

  private DrsMetadata fetchObject(
      DrsProvider drsProvider,
      CloudPlatformEnum cloudPlatform,
      List<String> requestedFields,
      Optional<ServiceName> serviceName,
      UriComponents uriComponents,
      String drsUri,
      BearerToken bearerToken,
      boolean forceAccessUrl,
      String ip,
      String googleProject,
      String transactionId) {

    AuditLogEvent.Builder auditEventBuilder =
        new AuditLogEvent.Builder()
            .dRSUrl(uriComponents.toUriString())
            .providerName(drsProvider.getName())
            .clientIP(Optional.ofNullable(ip))
            .serviceName(serviceName);

    final DrsObject drsResponse;
    final List<DrsHubAuthorization> authorizations;

    if (Fields.shouldRequestObjectInfo(requestedFields)) {
      try {
        authorizations = authService.buildAuthorizations(drsProvider, uriComponents, bearerToken);
        drsResponse =
            fetchObjectInfo(
                drsProvider, uriComponents, drsUri, bearerToken, authorizations, transactionId);
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
          googleProject,
          transactionId);
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
      String googleProject,
      String transactionId) {

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
                googleProject,
                transactionId);
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
      List<DrsHubAuthorization> authorizations,
      String transactionId) {
    var sendMetadataAuth = drsProvider.metadataAuthTypeIsSet();

    var objectId = getObjectId(uriComponents);
    String drsRequestLogMessage =
        "Requesting DRS metadata for %s with auth required %s from host %s"
            .formatted(drsUri, sendMetadataAuth, uriComponents.getHost());
    log.info(drsRequestLogMessage);

    var drsApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
    drsApi.setHeader(TRANSACTION_ID_HEADER_NAME, transactionId);
    if (sendMetadataAuth) {
      if (drsProvider.getMetadataAuthType() == DrsAuthEnum.passport
          || authorizations.stream()
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
      // note that the above if block will return early if passport is requested and is successful
      // if it is not required or successful, we set the bearer token
      drsApi.setBearerToken(
          authService.getMetadataAuthBearerToken(drsProvider, uriComponents, bearerToken));
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
      String ip,
      String googleProject,
      String transactionId) {

    var drsApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
    var objectId = getObjectId(uriComponents);
    var accessMethodConfig = drsProvider.getAccessMethodByType(accessMethodType);
    boolean retryMode =
        accessMethodConfig != null && accessMethodConfig.requiresUserProjectOnRetry();

    // Set x-user-project immediately for providers that always want it
    if (!retryMode && googleProject != null) {
      drsApi.setHeader("x-user-project", googleProject);
    }
    addStandardHeaders(drsApi, ip, transactionId);

    for (var authorization : drsHubAuthorizations) {
      AccessURL accessUrl;
      try {
        accessUrl =
            callAccessUrl(
                drsApi,
                objectId,
                accessId,
                authorization,
                accessMethodType,
                uriComponents,
                googleProject,
                drsHubAuthorizations,
                tdrApiFactory);
      } catch (HttpClientErrorException.BadRequest e) {
        if (retryMode && googleProject != null && isRequireUserProjectError(e)) {
          // Retry with a fresh client that includes x-user-project
          var retryApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
          addStandardHeaders(retryApi, ip, transactionId);
          retryApi.setHeader("x-user-project", googleProject);
          accessUrl =
              callAccessUrl(
                  retryApi,
                  objectId,
                  accessId,
                  authorization,
                  accessMethodType,
                  uriComponents,
                  googleProject,
                  drsHubAuthorizations,
                  tdrApiFactory);
        } else {
          throw e;
        }
      }
      if (accessUrl != null) {
        auditLogEventBuilder.authType(
            drsProvider.getAccessMethodByType(accessMethodType).getAuth());
        return accessUrl;
      }
    }
    return null;
  }

  private static AccessURL callAccessUrl(
      DrsApi drsApi,
      String objectId,
      String accessId,
      DrsHubAuthorization authorization,
      TypeEnum accessMethodType,
      UriComponents uriComponents,
      String googleProject,
      List<DrsHubAuthorization> drsHubAuthorizations,
      TdrApiFactory tdrApiFactory) {
    Optional<List<String>> auth =
        authorization.getAuthForAccessMethodType().apply(accessMethodType);

    return switch (authorization.drsAuthType()) {
      case NONE -> drsApi.getAccessURL(objectId, accessId);
      case BASICAUTH ->
          throw new BadRequestException(
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
          // If googleProject is set, also send bearer token alongside passports to enable
          // signing the access url with the userProject set with the right access
          if (googleProject != null) {
            log.info(
                "Google project {} specified for passport auth request to {}. Attempting to include bearer token.",
                googleProject,
                uriComponents.toUriString());
            // Find BEARERAUTH authorization in the list to get the properly configured token
            Optional<String> bearerTokenOpt =
                drsHubAuthorizations.stream()
                    .filter(a -> a.drsAuthType() == Authorizations.SupportedTypesEnum.BEARERAUTH)
                    .findFirst()
                    .flatMap(a -> a.getAuthForAccessMethodType().apply(accessMethodType))
                    .flatMap(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
            if (bearerTokenOpt.isPresent()) {
              log.info(
                  "Setting bearer token for passport auth request to {}",
                  uriComponents.toUriString());
              // For this specific case, call TDR using the TDR client instead of the DRS client.
              // The TDR client supports passing the bearer token in the request.
              yield auth.map(a -> callDataRepoPostAccessUrl(tdrApiFactory, bearerTokenOpt.get(), a, objectId, accessId, googleProject, "https://" + uriComponents.getHost())).orElse(null);
            } else {
              log.warn(
                  "Google project specified but no bearer token found in authorizations for {}",
                  uriComponents.toUriString());
            }
          }
          yield auth.map(a -> drsApi.postAccessURL(Map.of("passports", a), objectId, accessId))
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
  }

  private static AccessURL callDataRepoPostAccessUrl(
      TdrApiFactory tdrApiFactory, String accessToken, List<String> passportStrings, String objectId, String accessId, String xUserProject, String tdrBaseUrl
  ) {
    DRSPassportRequestModel body = new DRSPassportRequestModel();
    body.setPassports(passportStrings);

    DataRepositoryServiceApi drsApi = tdrApiFactory.getApi(accessToken, tdrBaseUrl);
    DRSAccessURL drsAccessURL;
    try {
      drsAccessURL = drsApi.postAccessURL(body, objectId, accessId, xUserProject);
    } catch (ApiException e) {
      var status = HttpStatusCode.valueOf(e.getCode());
      var responseBody = e.getResponseBody() != null ? e.getResponseBody().getBytes(StandardCharsets.UTF_8) : new byte[0];
      if (status.is4xxClientError()) {
        throw HttpClientErrorException.create(status, e.getMessage(), HttpHeaders.EMPTY, responseBody, StandardCharsets.UTF_8);
      }
      throw HttpServerErrorException.create(status, e.getMessage(), HttpHeaders.EMPTY, responseBody, StandardCharsets.UTF_8);
    }

    // translate the ga4gh client model to the TDR client model for the response
    AccessURL accessURL = new AccessURL();
    accessURL.setUrl(drsAccessURL.getUrl());
    accessURL.setHeaders(drsAccessURL.getHeaders());

    return accessURL;
  }


  private static boolean isRequireUserProjectError(HttpClientErrorException.BadRequest e) {
    return e.getResponseBodyAsString().contains("Snapshot requires an x-user-project header");
  }

  private static void addStandardHeaders(DrsApi drsApi, String ip, String transactionId) {
    if (ip != null) {
      drsApi.setHeader("X-Forwarded-For", ip);
    }
    drsApi.setHeader(TRANSACTION_ID_HEADER_NAME, transactionId);
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
      List<String> requestedFields, DrsMetadata drsMetadata, DrsProvider drsProvider) {

    return AnnotatedResourceMetadata.builder()
        .requestedFields(requestedFields)
        .drsMetadata(drsMetadata)
        .drsProvider(drsProvider)
        .build();
  }

  public String getTransactionId() {
    return UUID.randomUUID().toString();
  }
}
