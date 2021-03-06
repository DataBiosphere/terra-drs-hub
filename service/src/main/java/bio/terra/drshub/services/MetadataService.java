package bio.terra.drshub.services;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.DrsHubException;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.DrsProviderInterface;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.AnnotatedResourceMetadata;
import bio.terra.drshub.models.DrsMetadata;
import bio.terra.drshub.models.Fields;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.DrsObject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public record MetadataService(
    BondApiFactory bondApiFactory,
    DrsApiFactory drsApiFactory,
    DrsHubConfig drsHubConfig,
    ExternalCredsApiFactory externalCredsApiFactory) {

  private static final Pattern drsRegex =
      Pattern.compile(
          "(?:dos|drs)://(?:(?<compactId>dg\\.[0-9a-z-]+)|(?<hostname>[^?/]+\\.[^?/]+))[:/](?<suffix>[^?]*)",
          Pattern.CASE_INSENSITIVE);

  public AnnotatedResourceMetadata fetchResourceMetadata(
      String drsUri,
      List<String> rawRequestedFields,
      BearerToken bearerToken,
      Boolean forceAccessUrl) {

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
            provider, requestedFields, uriComponents, drsUri, bearerToken, forceAccessUrl);

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

  private DrsMetadata fetchMetadata(
      DrsProvider drsProvider,
      List<String> requestedFields,
      UriComponents uriComponents,
      String drsUri,
      BearerToken bearerToken,
      boolean forceAccessUrl) {
    var drsMetadataBuilder = new DrsMetadata.Builder();

    var drsResponse =
        maybeFetchDrsObject(drsProvider, requestedFields, uriComponents, drsUri, bearerToken);
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

        var passports = maybeFetchPassports(drsProvider, bearerToken, accessMethodType);

        try {
          var providerAccessMethod = drsProvider.getAccessMethodByType(accessMethodType);

          log.info("Requesting URL for {}", uriComponents.toUriString());

          var accessUrl =
              getAccessUrl(
                  drsProvider,
                  uriComponents,
                  bearerToken,
                  forceAccessUrl,
                  accessMethodType,
                  accessId,
                  passports,
                  providerAccessMethod.getAuth(),
                  false);

          if (accessUrl == null && providerAccessMethod.getFallbackAuth().isPresent()) {
            drsMetadataBuilder.accessUrl(
                getAccessUrl(
                    drsProvider,
                    uriComponents,
                    bearerToken,
                    forceAccessUrl,
                    accessMethodType,
                    accessId,
                    passports,
                    providerAccessMethod.getFallbackAuth().get(),
                    true));
          } else {
            drsMetadataBuilder.accessUrl(accessUrl);
          }
        } catch (RuntimeException e) {
          if (DrsProviderInterface.shouldFailOnAccessUrlFail(accessMethodType)) {
            throw e;
          } else {
            log.warn("Ignoring error from fetching signed URL", e);
          }
        }
      }
    }

    return drsMetadataBuilder.build();
  }

  private DrsObject maybeFetchDrsObject(
      DrsProvider drsProvider,
      List<String> requestedFields,
      UriComponents uriComponents,
      String drsUri,
      BearerToken bearerToken) {
    if (Fields.shouldRequestMetadata(requestedFields)) {
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
    return null;
  }

  private List<String> maybeFetchPassports(
      DrsProvider drsProvider, BearerToken bearerToken, TypeEnum accessMethodType) {
    if (drsProvider.shouldFetchPassports(accessMethodType)) {
      var ecmApi = externalCredsApiFactory.getApi(bearerToken.getToken());

      try {
        // For now, we are only getting a RAS passport. In the future it may also fetch from other
        // providers.
        return List.of(ecmApi.getProviderPassport("ras"));
      } catch (HttpStatusCodeException e) {
        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
          log.info("User does not have a passport.");
        } else {
          throw e;
        }
      }
    }
    return null;
  }

  private AccessURL getAccessUrl(
      DrsProvider drsProvider,
      UriComponents uriComponents,
      BearerToken bearerToken,
      boolean forceAccessUrl,
      TypeEnum accessMethodType,
      String accessId,
      List<String> passports,
      AccessUrlAuthEnum providerAccessMethodType,
      boolean useFallbackAuth) {

    var accessToken =
        getFenceAccessToken(
            uriComponents.toUriString(),
            accessMethodType,
            useFallbackAuth,
            drsProvider,
            forceAccessUrl,
            bearerToken);

    var drsApi = drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider);
    var objectId = getObjectId(uriComponents);

    switch (providerAccessMethodType) {
      case passport:
        if (!isEmpty(passports)) {
          try {
            return drsApi.postAccessURL(Map.of("passports", passports), objectId, accessId);
          } catch (RestClientException e) {
            log.error(
                "Passport authorized request failed for {} with error {}",
                uriComponents.toUriString(),
                e.getMessage());
          }
        }
        // if we made it this far, there are no passports or there was an error using them so return
        // nothing.
        return null;
      case current_request:
        accessToken.ifPresent(drsApi::setBearerToken);
        return drsApi.getAccessURL(objectId, accessId);
      case fence_token:
        if (accessToken.isPresent()) {
          drsApi.setBearerToken(accessToken.get());
          return drsApi.getAccessURL(objectId, accessId);
        } else {
          throw new BadRequestException(
              String.format(
                  "Fence access token required for %s but is missing. Does user have an account linked in Bond?",
                  uriComponents.toUriString()));
        }
      default:
        throw new DrsHubException("This should be impossible, unknown auth type");
    }
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

  private Optional<String> getFenceAccessToken(
      String drsUri,
      TypeEnum accessMethodType,
      boolean useFallbackAuth,
      DrsProvider drsProvider,
      boolean forceAccessUrl,
      BearerToken bearerToken) {
    if (drsProvider.shouldFetchFenceAccessToken(
        accessMethodType, useFallbackAuth, forceAccessUrl)) {

      log.info(
          "Requesting Bond access token for '{}' from '{}'",
          drsUri,
          drsProvider.getBondProvider().orElseThrow());

      var bondApi = bondApiFactory.getApi(bearerToken);

      var response =
          bondApi.getLinkAccessToken(drsProvider.getBondProvider().orElseThrow().getUriValue());

      return Optional.ofNullable(response.getToken());
    } else {
      return Optional.empty();
    }
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
