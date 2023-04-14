package bio.terra.drshub.services;

import bio.terra.bond.model.SaKeyObject;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.DrsHubException;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.DrsHubAuthorization;
import com.google.common.annotations.VisibleForTesting;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.Authorizations;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;

@Service
@Slf4j
public class AuthService {

  private final BondApiFactory bondApiFactory;
  private final DrsApiFactory drsApiFactory;
  private final ExternalCredsApiFactory externalCredsApiFactory;

  // To avoid absolutely hammering the ECM API during large batch analyses,
  // cache the passport for a given bearer token for just a little bit.
  // This also keeps DRSHub from calling ECM twice for the same request
  // if the object info endpoint needs passport auth as well as the object access url endpoint.
  private final Map<String, Optional<List<String>>> passportCache =
      Collections.synchronizedMap(new PassiveExpiringMap<>(1, TimeUnit.MINUTES));

  public AuthService(
      BondApiFactory bondApiFactory,
      DrsApiFactory drsApiFactory,
      ExternalCredsApiFactory externalCredsApiFactory) {
    this.bondApiFactory = bondApiFactory;
    this.drsApiFactory = drsApiFactory;
    this.externalCredsApiFactory = externalCredsApiFactory;
  }

  /**
   * Get the SA key for a user from Bond
   *
   * @param drsProvider Drs provider to provide the fence to get SA key for
   * @param bearerToken bearer token of the user
   * @return The SA key
   */
  public SaKeyObject fetchUserServiceAccount(DrsProvider drsProvider, BearerToken bearerToken) {
    var bondApi = bondApiFactory.getApi(bearerToken);
    return bondApi.getLinkSaKey(drsProvider.getBondProvider().orElseThrow().getUriValue());
  }

  /**
   * Build the authorizations that will enable the token/passport getting at runtime.
   * DrsHubAuthorizations provide lazily-evaluated tokens based on access type.
   *
   * @param drsProvider Drs provider being reached out to
   * @param components URI Components of the drs object
   * @param bearerToken bearer token of the current user
   * @return A list of DrsHubAuthorizations that can be used to get a token/passport based on object
   *     info.
   */
  public List<DrsHubAuthorization> buildAuthorizations(
      DrsProvider drsProvider, UriComponents components, BearerToken bearerToken) {
    return fetchDrsAuthorizations(drsProvider, components)
        .map(auths -> getDrsAuths(auths, drsProvider, components, bearerToken))
        .orElse(getAccessMethodConfigAuths(drsProvider, components, bearerToken));
  }

  // Translate the Authorizations response from a Drs Provider to the DrsHubAuthorizations
  // used in the rest of the codebase
  private List<DrsHubAuthorization> getDrsAuths(
      Authorizations auths,
      DrsProvider drsProvider,
      UriComponents components,
      BearerToken bearerToken) {
    return auths.getSupportedTypes().stream()
        .map(authType -> mapDrsAuthType(authType, drsProvider, components, bearerToken))
        .toList();
  }

  // For a given Authorization type returned by the Drs Provider,
  // build the DrsHubAuthorization that maps that auth type to the function that lazily gets the
  // bearer/fence token or passport.
  private DrsHubAuthorization mapDrsAuthType(
      Authorizations.SupportedTypesEnum authType,
      DrsProvider drsProvider,
      UriComponents components,
      BearerToken bearerToken) {
    return switch (authType) {
      case NONE -> new DrsHubAuthorization(
          authType, (AccessMethod.TypeEnum accessType) -> Optional.empty());
      case BASICAUTH -> throw new DrsHubException(
          "DRSHub does not currently support basic username/password authentication");
      case BEARERAUTH -> new DrsHubAuthorization(
          authType,
          (AccessMethod.TypeEnum accessType) ->
              switch (drsProvider.getAccessMethodByType(accessType).getAuth()) {
                case fence_token -> getFenceAccessToken(
                    components.toUriString(), drsProvider, bearerToken);
                case current_request -> Optional.ofNullable(bearerToken.getToken()).map(List::of);
                  // The passport case is weird. The provider needs a bearer auth,
                  // but configs say this provider should be using a passport.
                  // Check to see if the fallback auth is current_request or fence_token
                case passport -> drsProvider
                    .getAccessMethodByType(accessType)
                    .getFallbackAuth()
                    .flatMap(
                        auth ->
                            switch (auth) {
                              case fence_token -> getFenceAccessToken(
                                  components.toUriString(), drsProvider, bearerToken);
                              case current_request -> Optional.ofNullable(bearerToken.getToken())
                                  .map(List::of);
                              default -> throw new DrsHubException(
                                  "Auth mismatch. DRS Provider requests bearer auth, but DRSHub config does not specify what bearer auth to use");
                            });
              });
      case PASSPORTAUTH -> new DrsHubAuthorization(
          authType, (AccessMethod.TypeEnum accessType) -> fetchPassports(bearerToken));
    };
  }

  // Called if there was no Authorizations response from the Drs Provider.
  // Translate what DRSHub stores in its config to the DrsHubAuthorizations used later in the code.
  private List<DrsHubAuthorization> getAccessMethodConfigAuths(
      DrsProvider drsProvider, UriComponents components, BearerToken bearerToken) {
    return drsProvider.getAccessMethodConfigs().stream()
        .flatMap(
            accessMethodConfig -> {
              DrsHubAuthorization primaryAuth =
                  mapAccessMethodConfigAuthType(
                      accessMethodConfig.getAuth(), drsProvider, components, bearerToken);
              if (accessMethodConfig.getFallbackAuth().isPresent()) {
                DrsHubAuthorization secondaryAuth =
                    mapAccessMethodConfigAuthType(
                        accessMethodConfig.getFallbackAuth().get(),
                        drsProvider,
                        components,
                        bearerToken);
                return Stream.of(primaryAuth, secondaryAuth);
              }
              return Stream.of(primaryAuth);
            })
        .toList();
  }

  // Map a GA4GH Authorization type to the types DRSHub keeps in its config.
  private DrsHubAuthorization mapAccessMethodConfigAuthType(
      AccessUrlAuthEnum authType,
      DrsProvider drsProvider,
      UriComponents components,
      BearerToken bearerToken) {
    return switch (authType) {
      case current_request -> new DrsHubAuthorization(
          Authorizations.SupportedTypesEnum.BEARERAUTH,
          accessType -> Optional.ofNullable(bearerToken.getToken()).map(List::of));
      case fence_token -> new DrsHubAuthorization(
          Authorizations.SupportedTypesEnum.BEARERAUTH,
          accessType -> getFenceAccessToken(components.toUriString(), drsProvider, bearerToken));
      case passport -> new DrsHubAuthorization(
          Authorizations.SupportedTypesEnum.PASSPORTAUTH,
          accessType -> fetchPassports(bearerToken));
    };
  }

  /**
   * Reach out to the Drs Provider's options endpoint for the object to get Authorizations required.
   *
   * @param drsProvider Drs Provider to reach out to
   * @param uriComponents DRS URI of the object to be resolved.
   * @return If the Drs Provider returns an Authorizations, return that in an Optional. Else, return
   *     the empty Optional.
   */
  @VisibleForTesting
  Optional<Authorizations> fetchDrsAuthorizations(
      DrsProvider drsProvider, UriComponents uriComponents) {
    var drsApi =
        Optional.ofNullable(drsApiFactory.getApiFromUriComponents(uriComponents, drsProvider));
    var objectId = uriComponents.getPath();
    try {
      return Optional.ofNullable(drsApi.orElseThrow().optionsObject(objectId));
    } catch (RestClientException ex) {
      log.warn(
          "Failed to get authorizations for {} from OPTIONS endpoint for DRS Provider {}. "
              + "Falling back to configured authorizations",
          objectId,
          drsProvider.getName());
      return Optional.empty();
    } catch (NoSuchElementException ex) {
      throw new DrsHubException(
          String.format(
              "Failed to initialize DrsApi for provider %s and uri components %s. "
                  + "You may have passed in a malformed DRS url, or we do not support this provider.",
              drsProvider.getName(), uriComponents.toUriString()));
    }
  }

  // Reach out to Bond and get the fence token for the user.
  private Optional<List<String>> getFenceAccessToken(
      String drsUri, DrsProvider drsProvider, BearerToken bearerToken) {
    log.info(
        "Requesting Bond access token for '{}' from '{}'",
        drsUri,
        drsProvider.getBondProvider().orElseThrow());

    var bondApi = bondApiFactory.getApi(bearerToken);

    var response =
        bondApi.getLinkAccessToken(drsProvider.getBondProvider().orElseThrow().getUriValue());

    return Optional.ofNullable(response.getToken()).map(List::of);
  }

  /**
   * Reach out to ECM and get the RAS Passport for the current user
   *
   * @param bearerToken token for the current user
   * @return An Optional list of passports tied to the user.
   */
  public Optional<List<String>> fetchPassports(BearerToken bearerToken) {
    return passportCache.computeIfAbsent(
        bearerToken.getToken(),
        token -> {
          var ecmApi = externalCredsApiFactory.getApi(bearerToken.getToken());
          try {
            // For now, we are only getting a RAS passport. In the future it may also fetch from
            // other
            // providers.
            return Optional.of(List.of(ecmApi.getProviderPassport("ras")));
          } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
              log.info("User does not have a passport.");
              return Optional.empty();
            } else {
              throw e;
            }
          }
        });
  }
}
