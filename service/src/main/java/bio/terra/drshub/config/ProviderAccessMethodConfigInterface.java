package bio.terra.drshub.config;

import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface ProviderAccessMethodConfigInterface {
  AccessMethodConfigTypeEnum getType();

  AccessUrlAuthEnum getAuth();

  boolean isFetchAccessUrl();

  Optional<AccessUrlAuthEnum> getFallbackAuth();
}
