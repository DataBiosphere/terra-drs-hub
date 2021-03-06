package bio.terra.drshub.models;

import bio.terra.bond.model.SaKeyObject;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.DrsObject;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface DrsMetadata {
  @Nullable
  DrsObject getDrsResponse();

  @Nullable
  String getFileName();

  @Nullable
  String getLocalizationPath();

  @Nullable
  AccessURL getAccessUrl();

  @Nullable
  SaKeyObject getBondSaKey();

  class Builder extends ImmutableDrsMetadata.Builder {}
}
