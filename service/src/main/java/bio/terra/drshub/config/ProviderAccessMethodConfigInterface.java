package bio.terra.drshub.config;

import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.DrsAuthEnum;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface ProviderAccessMethodConfigInterface {
  AccessMethodConfigTypeEnum getType();

  DrsAuthEnum getAuth();

  boolean isFetchAccessUrl();

  Optional<DrsAuthEnum> getFallbackAuth();
}
