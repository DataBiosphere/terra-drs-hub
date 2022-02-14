package bio.terra.drshub.services;

import bio.terra.bond.api.BondApi;
import bio.terra.common.exception.BadRequestException;
import bio.terra.drshub.DrsHubException;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.DrsProviderInterface;
import bio.terra.drshub.generated.model.ResourceMetadata;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.DrsMetadata;
import bio.terra.drshub.models.Fields;
import bio.terra.externalcreds.api.OidcApi;
import io.github.ga4gh.drs.api.ObjectsApi;
import io.github.ga4gh.drs.client.auth.OAuth;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.Checksum;
import io.github.ga4gh.drs.model.DrsObject;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class MetadataService {

  /**
   * DOS or DRS schemes are allowed as of <a
   * href="https://ucsc-cgl.atlassian.net/browse/AZUL-702">AZUL-702</a>
   *
   * <p>The many, many forms of Compact Identifier-based (CIB) DRS URIs to W3C/IETF HTTPS URL
   * conversion:
   *
   * <ul>
   *   <li>https://ga4gh.github.io/data-repository-service-schemas/preview/release/drs-1.1.0/docs/#_compact_identifier_based_drs_uris
   *   <li>https://docs.google.com/document/d/1Wf4enSGOEXD5_AE-uzLoYqjIp5MnePbZ6kYTVFp1WoM/edit
   *   <li>https://broadworkbench.atlassian.net/browse/BT-4?focusedCommentId=35980
   *   <li>etc.
   * </ul>
   *
   * <p>Note: GA4GH CIB URIs are incompatible with W3C/IETF URIs and the various standard libraries
   * that parse them:
   *
   * <ul>
   *   <li>https://en.wikipedia.org/wiki/Uniform_Resource_Identifier#Definition
   *   <li>https://tools.ietf.org/html/rfc3986
   *   <li>https://cr.openjdk.java.net/~dfuchs/writeups/updating-uri/
   *   <li>etc.
   * </ul>
   *
   * <p>Additionally, there are previous non-CIB DOS/DRS URIs that *are* compatible with W3C/IETF
   * URIs format too. Instead of encoding the `/` in the protocol suffix to `%2F` they seem to pass
   * it through just as a `/` in the HTTPS URL.
   *
   * <p>If you update *any* of the below be sure to link to the supporting docs and update the
   * comments above!
   */
  private static final Pattern compactIdRegex =
      Pattern.compile(
          "(?:dos|drs)://(?<host>dg\\.[0-9a-z-]+)[:/](?<suffix>[^?]*)(?<query>\\?(.*))?",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern gsUriParseRegex =
      Pattern.compile("gs://(?<bucket>[^/]+)/(?<name>.+)", Pattern.CASE_INSENSITIVE);

  private final DrsHubConfig drsHubConfig;

  public MetadataService(DrsHubConfig drsHubConfig) {
    this.drsHubConfig = drsHubConfig;
  }

  public ResourceMetadata fetchResourceMetadata(
      String drsUri, List<String> rawRequestedFields, String accessToken, Boolean forceAccessUrl) {

    var requestedFields =
        (rawRequestedFields == null || rawRequestedFields.isEmpty())
            ? Fields.DEFAULT_FIELDS
            : rawRequestedFields;

    var uriComponents = getUriComponents(drsUri);
    var provider = determineDrsProvider(uriComponents);

    log.info(
        "Drs URI '{}' will use provider {}, requested fields {}",
        drsUri,
        provider.getName(),
        String.join(", ", requestedFields));

    var metadata =
        fetchMetadata(
            provider, requestedFields, uriComponents, drsUri, accessToken, forceAccessUrl);

    return buildResponseObject(requestedFields, metadata, provider);
  }

  private UriComponents getUriComponents(String drsUri) {

    var compactIdMatch = compactIdRegex.matcher(drsUri);

    if (compactIdMatch.matches()) {

      return UriComponentsBuilder.newInstance()
          .host(drsHubConfig.expandCibHost(compactIdMatch.group("host")))
          //              Objects.requireNonNull(
          //
          // drsHubConfig.getHosts().get(Helpers.determineCibBondProvider(cibHost)),
          //                  String.format("Unrecognized Compact Identifier Based host [%s]",
          // cibHost)))
          .path(URLEncoder.encode(compactIdMatch.group("suffix"), StandardCharsets.UTF_8))
          .query(compactIdMatch.group("query"))
          .build();
    } else {
      var parsedUri = UriComponentsBuilder.fromUriString(drsUri).build();
      if (parsedUri.getHost() == null || parsedUri.getPath() == null) {
        throw new BadRequestException(
            String.format("[%s] is missing a host and/or a path.", drsUri));
      }
      return parsedUri;
    }
  }

  private DrsProvider determineDrsProvider(UriComponents uriComponents) {
    var host = uriComponents.getHost();
    assert host != null;

    if (host.endsWith("dataguids.org")) {
      throw new BadRequestException(
          "dataguids.org data has moved. See: https://support.terra.bio/hc/en-us/articles/360060681132");
    }

    var providers = drsHubConfig.getDrsProviders().values();

    return providers.stream()
        .filter(p -> host.matches(p.getHostRegex()))
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    String.format(
                        "Could not determine DRS provider for id `%s`",
                        uriComponents.toUriString())));
  }

  private String getObjectId(UriComponents uriComponents) {
    // TODO: is there a reason we need query params? it breaks getAccessUrl.
    return uriComponents.getPath();
  }

  private Optional<AccessURL> getAccessUrl(
      AccessUrlAuthEnum accessUrlAuth,
      UriComponents uriComponents,
      String accessId,
      Optional<String> accessToken,
      List<String> passports)
      throws RestClientException {

    var drsApi = makeDrsApiFromDrsUriComponents(uriComponents);
    var objectId = getObjectId(uriComponents);

    switch (accessUrlAuth) {
      case passport:
        if (passports != null && !passports.isEmpty()) {
          try {
            return Optional.ofNullable(
                drsApi.postAccessURL(Map.of("passports", passports), objectId, accessId));
          } catch (RestClientException e) {
            log.error(
                "Passport authorized request failed for {} with error {}",
                uriComponents.toUriString(),
                e.getMessage());
          }
        }
        // if we made it this far, there are no passports or there was an error using them so return
        // nothing.
        return Optional.empty();
      case current_request:
        drsApi.getApiClient().setAccessToken(accessToken.orElse(null));
        return Optional.ofNullable(drsApi.getAccessURL(objectId, accessId));
      case fence_token:
        if (accessToken.isPresent()) {
          drsApi.getApiClient().setAccessToken(accessToken.get());
          return Optional.ofNullable(drsApi.getAccessURL(objectId, accessId));
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

  private Optional<String> getFenceAccessToken(
      String drsUri,
      Optional<AccessMethod> accessMethod,
      boolean useFallbackAuth,
      DrsProvider drsProvider,
      List<String> requestedFields,
      boolean forceAccessUrl,
      String bearerToken) {
    if (drsProvider.shouldFetchFenceAccessToken(
        accessMethod.map(AccessMethod::getType).orElse(null),
        requestedFields,
        useFallbackAuth,
        forceAccessUrl)) {

      log.info(
          "Requesting Bond access token for '{}' from '{}'", drsUri, drsProvider.getBondProvider());

      var bondApi = new BondApi();
      bondApi.getApiClient().setBasePath(drsHubConfig.getBondUrl());
      bondApi.getApiClient().setAccessToken(bearerToken);

      var response =
          bondApi.getLinkAccessToken(drsProvider.getBondProvider().orElseThrow().toString());

      return Optional.ofNullable(response.getToken());
    } else {
      return Optional.empty();
    }
  }

  private DrsMetadata fetchMetadata(
      DrsProvider drsProvider,
      List<String> requestedFields,
      UriComponents uriComponents,
      String drsUri,
      String bearerToken,
      boolean forceAccessField) {
    DrsObject drsResponse = null;
    if (Fields.shouldRequestMetadata(requestedFields)) {

      var objectId = getObjectId(uriComponents);
      var sendMetadataAuth = drsProvider.isMetadataAuth();
      log.info(
          "Requesting DRS metadata for '{}' with auth required '{}'", drsUri, sendMetadataAuth);

      var drsApi = makeDrsApiFromDrsUriComponents(uriComponents);
      if (sendMetadataAuth) {
        ((OAuth) drsApi.getApiClient().getAuthentication("BearerAuth")).setAccessToken(bearerToken);
      }

      drsResponse = drsApi.getObject(objectId, null);
    }

    var accessMethod = getAccessMethod(drsResponse, drsProvider);
    // TODO: make this optional instead of icky null
    var accessMethodType = accessMethod.map(AccessMethod::getType).orElse(null);

    String bondSaKey = null;
    if (drsProvider.shouldFetchUserServiceAccount(accessMethodType, requestedFields)) {
      var bondApi = new BondApi();
      bondApi.getApiClient().setBasePath(drsHubConfig.getBondUrl());
      bondApi.getApiClient().setAccessToken(bearerToken);

      // TODO: are we getting the key in a usable format?
      bondSaKey =
          bondApi
              .getLinkSaKey(drsProvider.getBondProvider().orElseThrow().toString())
              .getData()
              .toString();
    }

    Optional<AccessURL> accessUrl = Optional.empty();
    List<String> passports = null;
    // TODO: is this an else-if?
    if (drsProvider.shouldFetchPassports(accessMethodType, requestedFields)) {

      var ecmApi = new OidcApi();
      ecmApi.getApiClient().setBasePath(drsHubConfig.getExternalcredsUrl());
      ecmApi.getApiClient().setAccessToken(bearerToken);

      try {
        // For now, we are only getting a RAS passport. In the future it may also fetch from other
        // providers.
        passports = List.of(ecmApi.getProviderPassport("ras"));
      } catch (HttpStatusCodeException e) {
        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
          log.info("User does not have a passport.");
        } else {
          throw e;
        }
      }
    }

    var fileName = getDrsFileName(drsResponse);
    var localizationPath = getLocalizationPath(drsProvider, drsResponse);

    try {
      var accessToken =
          getFenceAccessToken(
              drsUri,
              accessMethod,
              false,
              drsProvider,
              requestedFields,
              forceAccessField,
              bearerToken);

      if (drsProvider.shouldFetchAccessUrl(accessMethodType, requestedFields, forceAccessField)) {
        var providerAccessMethod = drsProvider.getAccessMethodByType(accessMethodType);

        log.info("Requesting URL for {}", uriComponents.toUriString());

        accessUrl =
            getAccessUrl(
                providerAccessMethod.getAuth(),
                uriComponents,
                accessMethod.map(AccessMethod::getAccessId).orElseThrow(),
                accessToken,
                passports);

        if (accessUrl.isEmpty() && providerAccessMethod.getFallbackAuth().isPresent()) {
          var fallbackToken =
              getFenceAccessToken(
                  drsUri,
                  accessMethod,
                  true,
                  drsProvider,
                  requestedFields,
                  forceAccessField,
                  bearerToken);

          accessUrl =
              getAccessUrl(
                  providerAccessMethod.getFallbackAuth().get(),
                  uriComponents,
                  accessMethod.map(AccessMethod::getAccessId).orElseThrow(),
                  fallbackToken,
                  passports);
        }
      }
    } catch (RuntimeException e) {
      if (DrsProviderInterface.shouldFailOnAccessUrlFail(accessMethodType)) {
        throw e;
      } else {
        log.warn("Ignoring error from fetching signed URL", e);
      }
    }

    return new DrsMetadata.Builder()
        .drsResponse(Optional.ofNullable(drsResponse))
        .fileName(fileName)
        .localizationPath(localizationPath)
        .accessUrl(accessUrl)
        .bondSaKey(Optional.ofNullable(bondSaKey))
        .build();
  }

  private ObjectsApi makeDrsApiFromDrsUriComponents(UriComponents uriComponents) {
    var drsApi = new ObjectsApi();
    var drsClient = drsApi.getApiClient();
    drsClient.setBasePath(
        drsClient
            .getBasePath()
            .replace("{serverURL}", Objects.requireNonNull(uriComponents.getHost())));

    return drsApi;
  }

  private Optional<AccessMethod> getAccessMethod(DrsObject drsResponse, DrsProvider drsProvider) {
    if (drsResponse != null && !drsResponse.getAccessMethods().isEmpty()) {
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
    if (drsResponse == null) {
      return Optional.empty();
    }

    if (drsResponse.getName() != null) {
      return Optional.of(drsResponse.getName());
    }

    var accessURL = drsResponse.getAccessMethods().get(0).getAccessUrl();

    if (accessURL != null) {
      var path = URI.create(accessURL.getUrl()).getPath();
      return Optional.ofNullable(path).map(s -> s.replaceAll("^.*[\\\\/]", ""));
    }

    return Optional.empty();
  }

  private Optional<String> getLocalizationPath(DrsProvider drsProvider, DrsObject drsResponse) {
    if (drsProvider.useAliasesForLocalizationPath()
        && drsResponse != null
        && drsResponse.getAliases() != null
        && !drsResponse.getAliases().isEmpty()) {
      return Optional.of(drsResponse.getAliases().get(0));
    }

    return Optional.empty();
  }

  private ResourceMetadata buildResponseObject(
      List<String> requestedFields, DrsMetadata drsMetadata, DrsProvider drsProvider) {

    var eventualResponse = new ResourceMetadata();

    for (var f : requestedFields) {
      if (f.equals(Fields.BOND_PROVIDER)) {
        drsProvider
            .getBondProvider()
            .ifPresent(p -> eventualResponse.setBondProvider(p.toString()));
      }

      if (f.equals(Fields.FILE_NAME)) {
        drsMetadata.getFileName().ifPresent(eventualResponse::setFileName);
      }
      if (f.equals(Fields.LOCALIZATION_PATH)) {
        drsMetadata.getLocalizationPath().ifPresent(eventualResponse::setLocalizationPath);
      }
      if (f.equals(Fields.ACCESS_URL)) {
        drsMetadata.getAccessUrl().ifPresent(eventualResponse::setAccessUrl);
      }
      if (f.equals(Fields.GOOGLE_SERVICE_ACCOUNT)) {
        drsMetadata.getBondSaKey().ifPresent(eventualResponse::setGoogleServiceAccount);
      }

      drsMetadata
          .getDrsResponse()
          .ifPresent(
              r -> {
                if (f.equals(Fields.TIME_CREATED)) {
                  eventualResponse.setTimeCreated(r.getCreatedTime());
                }
                if (f.equals(Fields.TIME_UPDATED)) {
                  eventualResponse.setTimeUpdated(r.getUpdatedTime());
                }
                if (f.equals(Fields.HASHES)) {
                  eventualResponse.setHashes(getHashesMap(r.getChecksums()));
                }
                if (f.equals(Fields.SIZE)) {
                  eventualResponse.setSize(r.getSize());
                }
                if (f.equals(Fields.CONTENT_TYPE)) {
                  eventualResponse.setContentType(r.getMimeType());
                }

                var gsUrl = MetadataService.getGcsAccessURL(r).map(AccessURL::getUrl);
                if (f.equals(Fields.GS_URI)) {
                  gsUrl.ifPresent(eventualResponse::setGsUri);
                }

                var gsFileInfo = gsUrl.map(gsUriParseRegex::matcher);
                if (gsFileInfo.map(Matcher::matches).orElse(false)) {
                  if (f.equals(Fields.BUCKET)) {
                    gsFileInfo.map(i -> i.group("bucket")).ifPresent(eventualResponse::setBucket);
                  }
                  if (f.equals(Fields.NAME)) {
                    gsFileInfo.map(i -> i.group("name")).ifPresent(eventualResponse::setName);
                  }
                }
              });
    }

    return eventualResponse;
  }

  private static Optional<AccessURL> getGcsAccessURL(DrsObject drsObject) {
    return drsObject.getAccessMethods().stream()
        .filter(m -> m.getType() == AccessMethod.TypeEnum.GS)
        .findFirst()
        .map(AccessMethod::getAccessUrl);
  }

  private Map<String, String> getHashesMap(List<Checksum> checksums) {
    return checksums.stream().collect(Collectors.toMap(Checksum::getType, Checksum::getChecksum));
  }
}
