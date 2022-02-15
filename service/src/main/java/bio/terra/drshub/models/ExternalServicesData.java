package bio.terra.drshub.models;

import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface ExternalServicesData extends WithExternalServicesData {
  Optional<String> getAccessToken();

  Optional<String> getPassport();

  Optional<String> getObjectId();

  Optional<String> getAccessId();

  Optional<String> getAccessUrl();

  Optional<String> getDrsHost();

  Optional<String> getPassportProvider();

  class Builder extends ImmutableExternalServicesData.Builder {}
}
