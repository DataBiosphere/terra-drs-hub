package bio.terra.drshub.config;

import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.DrsAuthEnum;
import java.util.Optional;
import org.immutables.value.Value;
import org.immutables.value.Value.Default;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface ProviderAccessMethodConfigInterface {
  AccessMethodConfigTypeEnum getType();

  DrsAuthEnum getAuth();

  boolean isFetchAccessUrl();

  Optional<DrsAuthEnum> getFallbackAuth();

  /**
   * When true, DrsHub first attempts the access-URL call WITHOUT forwarding x-user-project. If the
   * provider returns 400 with an indication that the user project is required, DrsHub retries with
   * the header. This allows TDR snapshots that set requireUserProject=true to direct DrsHub to bill
   * the caller's project, without requiring callers to special-case TDR.
   */
  @Default
  default boolean requiresUserProjectOnRetry() {
    return false;
  }
}
