package bio.terra.drshub.config;

import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.BondProviderEnum;
import bio.terra.drshub.models.Fields;
import io.github.ga4gh.drs.model.AccessMethod;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;
import org.immutables.value.Value.Default;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface DrsProviderInterface {

  String getName();

  String getHostRegex();

  boolean isMetadataAuth();

  Optional<BondProviderEnum> getBondProvider();

  ArrayList<ProviderAccessMethodConfig> getAccessMethodConfigs();

  @Nullable
  MTlsConfig getMTlsConfig();

  @Value.Modifiable
  @PropertiesInterfaceStyle
  interface MTlsConfigInterface {
    String getKeyPath();

    String getCertPath();
  }

  /**
   * This is hopefully a temporary measure until we can take the time to either get a new field
   * added to the DRS spec or implement a temporary spec extension with the Terra Data Repo team.
   * See BT-417 for more details.
   */
  @Default
  default boolean useAliasesForLocalizationPath() {
    return false;
  }

  default ProviderAccessMethodConfig getAccessMethodByType(AccessMethod.TypeEnum accessMethodType) {
    return getAccessMethodConfigs().stream()
        .filter(o -> o.getType().getReturnedEquivalent() == accessMethodType)
        .findFirst()
        .orElse(null);
  }

  default List<AccessMethodConfigTypeEnum> getAccessMethodConfigTypes() {
    return getAccessMethodConfigs().stream().map(ProviderAccessMethodConfig::getType).toList();
  }

  /**
   * Should Drshub call Bond to retrieve a Fence access token to use when later calling the `access`
   * endpoint to retrieve a signed URL. Should return `true` for Gen3 signed URL flows and `false`
   * otherwise, including TDR signed URL flows (TDR uses the same auth supplied to the current
   * Drshub request for calling `access`).
   *
   * @param useFallbackAuth if false (default) check accessUrlAuth in accessMethods, otherwise check
   *     fallbackAccessUrlAuth
   */
  default boolean shouldFetchFenceAccessToken(
      AccessMethod.TypeEnum accessMethodType, boolean useFallbackAuth, boolean forceAccessUrl) {
    return getBondProvider().isPresent()
        && (forceAccessUrl
            || getAccessMethodConfigs().stream()
                .anyMatch(
                    m -> {
                      var accessMethodTypeMatches =
                          m.getType().getReturnedEquivalent() == accessMethodType;
                      var validFallbackAuth =
                          !useFallbackAuth
                              || m.getFallbackAuth().orElse(null) == AccessUrlAuthEnum.fence_token;
                      var validAccessAuth =
                          useFallbackAuth || m.getAuth() == AccessUrlAuthEnum.fence_token;

                      return accessMethodTypeMatches
                          && validFallbackAuth
                          && validAccessAuth
                          && m.isFetchAccessUrl();
                    }));
  }

  /** Should Drshub call the DRS provider's `access` endpoint to get a signed URL. */
  default boolean shouldFetchAccessUrl(
      AccessMethod.TypeEnum accessMethodType,
      List<String> requestedFields,
      boolean forceAccessUrl) {
    var fieldsOverlap = Fields.overlap(requestedFields, Fields.ACCESS_URL_FIELDS);
    var accessMethodConfigs = getAccessMethodConfigs();
    var accessMethodTypeMatches =
        accessMethodConfigs.stream()
            .anyMatch(
                m ->
                    m.getType().getReturnedEquivalent() == accessMethodType
                        && m.isFetchAccessUrl());

    return fieldsOverlap && (accessMethodTypeMatches || forceAccessUrl);
  }

  /**
   * Should Drshub fetch the Google user service account from Bond. Because this account is
   * Google-specific it should not be fetched if we know the underlying data is not GCS-based.
   */
  default boolean shouldFetchUserServiceAccount(
      AccessMethod.TypeEnum accessMethodType, List<String> requestedFields) {
    // This account would be stored in Bond so no Bond means no account.
    return getBondProvider().isPresent()
        // "Not definitely not GCS". A falsy accessMethod is okay because there may not have been a
        // preceding metadata request to determine the accessMethod.
        && (accessMethodType == null
            || AccessMethodConfigTypeEnum.gs.getReturnedEquivalent() == accessMethodType)
        && getAccessMethodConfigTypes().contains(AccessMethodConfigTypeEnum.gs)
        && Fields.overlap(requestedFields, Fields.BOND_SA_FIELDS);
  }

  /**
   * Fail this request if Drshub was unable to get an access/signed URL and the access method is
   * truthy but its type is not GCS. Drshub clients currently can't deal with cloud paths other than
   * GCS so there isn't a fallback way of accessing the object. Note: TDR's metadata responses for
   * objects stored in GCS include an `https` access method with a URL and headers (the headers
   * containing the same bearer auth as in the TDR metadata request) which could be used for
   * download without fetching a signed URL. However this presumes that the URL downloaders in
   * Drshub clients support headers, which at the time of this writing is not true at least for the
   * Cromwell localizer using getm 0.0.4. This also presumes that the Drshub response would fall
   * back to a different access method than the GCS/Azure one for which Drshub tried and failed to
   * get a signed URL. The current code does not support this.
   */
  static boolean shouldFailOnAccessUrlFail(AccessMethod.TypeEnum accessMethodType) {
    // TODO: get rid of this
    return AccessMethodConfigTypeEnum.gs.getReturnedEquivalent() != accessMethodType;
  }
}
