package bio.terra.drshub.models;

import bio.terra.bond.model.SaKeyObject;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.DrsObject;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface DrsMetadata extends WithDrsMetadata {
  Optional<DrsObject> getDrsResponse();

  Optional<String> getFileName();

  Optional<String> getLocalizationPath();

  Optional<AccessURL> getAccessUrl();

  Optional<SaKeyObject> getBondSaKey();

  class Builder extends ImmutableDrsMetadata.Builder {}
}
