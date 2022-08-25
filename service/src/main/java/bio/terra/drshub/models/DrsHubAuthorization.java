package bio.terra.drshub.models;

import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.Authorizations;
import java.util.Optional;
import java.util.function.Function;

public record DrsHubAuthorization(
    Authorizations.SupportedTypesEnum drsAuthType,
    Function<AccessMethod.TypeEnum, Optional<Object>> getAuthForAccessMethodType) {}
