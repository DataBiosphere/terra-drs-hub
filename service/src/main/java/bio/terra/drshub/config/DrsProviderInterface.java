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
   * Like {@link #getAccessMethodByType}, but corrects for TDR returning its GCP passport signed-URL
   * access method typed {@code https} (the same type it uses for Azure) rather than {@code gs}.
   * Only TDR prefixes Azure access ids with {@code az-}; any other {@code https} access id is
   * treated as GCP and resolved against the provider's {@code gs} config instead, regardless of the
   * type DRS reported. See CTM-613.
   */
  default ProviderAccessMethodConfig getAccessMethodByTypeAndAccessId(
      AccessMethod.TypeEnum accessMethodType, String accessId) {
    boolean isAzure = accessId != null && accessId.startsWith("az-");
    if (accessMethodType == AccessMethod.TypeEnum.HTTPS && !isAzure) {
      var gcpConfig = getAccessMethodByType(AccessMethod.TypeEnum.GS);
      if (gcpConfig != null) {
        return gcpConfig;
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
