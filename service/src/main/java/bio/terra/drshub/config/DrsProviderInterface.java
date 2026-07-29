package bio.terra.drshub.config;

import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.DrsAuthEnum;
import bio.terra.drshub.models.ECMProviderEnum;
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

  DrsAuthEnum getMetadataAuthType();

  Optional<ECMProviderEnum> getEcmProvider();

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

  /**
   * Select the access-method config for a resolved access method by its DRS `type` AND DRS 1.5
   * `cloud`. Matching on `type` alone is insufficient: a signed URL is `https` for every cloud, so a
   * GCS passport access method (typed `https`) would otherwise match the Azure `https` config and
   * lose requester-pays user-project support (the HTTP 500). When the access method carries no
   * `cloud` (providers not yet emitting DRS 1.5), or no cloud-annotated config matches, fall back to
   * the legacy type-only match so behavior is unchanged.
   */
  default ProviderAccessMethodConfig getAccessMethodConfig(
      AccessMethod.TypeEnum accessMethodType, @Nullable String cloud) {
    if (cloud != null) {
      Optional<ProviderAccessMethodConfig> byTypeAndCloud =
          getAccessMethodConfigs().stream()
              .filter(c -> c.getType().getReturnedEquivalent() == accessMethodType)
              .filter(c -> c.getCloud().map(cloud::equalsIgnoreCase).orElse(false))
              .findFirst();
      if (byTypeAndCloud.isPresent()) {
        return byTypeAndCloud.get();
      }
    }
    return getAccessMethodByType(accessMethodType);
  }

  default List<AccessMethodConfigTypeEnum> getAccessMethodConfigTypes() {
    return getAccessMethodConfigs().stream().map(ProviderAccessMethodConfig::getType).toList();
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
