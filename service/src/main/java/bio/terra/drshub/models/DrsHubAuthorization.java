package bio.terra.drshub.models;

import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.Authorizations;
import java.util.Optional;
import java.util.function.Function;

/**
 * DrsHubAuthorization enables the mapping of an Authentication returned from a DRS Provider's
 * Options endpoint to the auth defined in DrsHub's config. Since the returned Authorization is only
 * available at runtime, but we have compile-time configs for each access type, which is only known
 * after we get object info, the `getAuthForAccessType` function allows the runtime type to be used
 * to get a lazily-evaluated bearer token or passport as needed.
 */
public record DrsHubAuthorization(
    Authorizations.SupportedTypesEnum drsAuthType,
    Function<AccessMethod.TypeEnum, Optional<Object>> getAuthForAccessMethodType) {}
