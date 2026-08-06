package bio.terra.drshub.util;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.generated.model.RequestObject.CloudPlatformEnum;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.DrsObject;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AccessMethodUtils {
  public static Optional<AccessMethod> getAccessMethod(
      DrsObject drsResponse, DrsProvider drsProvider, CloudPlatformEnum cloudPlatform) {
    Optional<AccessMethod> accessMethod = Optional.empty();
    if (!isEmpty(drsResponse)) {
      List<AccessMethod> accessMethods = getAccessMethods(drsResponse, drsProvider);
      if (cloudPlatform != null) {
        accessMethod = getAccessMethodForCloud(accessMethods, cloudPlatform);
      }
      // if there is no access method matching the cloudPlatform, or
      // if the cloudPlatform was not specified, return the first access method
      if (accessMethod.isEmpty() && !accessMethods.isEmpty()) {
        accessMethod = accessMethods.stream().findFirst();
      }
    }
    return accessMethod;
  }

  public static Optional<AccessMethod> getAccessMethodForCloud(
      List<AccessMethod> accessMethods, CloudPlatformEnum cloudPlatform) {
    // Prefer the DRS 1.5 `cloud` field when the provider emits it: it names the CSP directly,
    // where `type` cannot (a signed URL is `https` for every cloud). Fall back to the legacy
    // heuristics (`type == gs` for GCS, `az` access-id prefix for Azure) for providers not yet
    // emitting `cloud`.
    String targetCloud = toDrsCloud(cloudPlatform);
    Optional<AccessMethod> byCloud =
        accessMethods.stream()
            .filter(m -> m.getCloud() != null && m.getCloud().equalsIgnoreCase(targetCloud))
            .findFirst();
    if (byCloud.isPresent()) {
      return byCloud;
    }

    Predicate<AccessMethod> filter;
    if (cloudPlatform.equals(CloudPlatformEnum.AZURE)) {
      // Note: Only the Terra Data Repo drs provider prefixes Azure access ids with "az"
      filter = m -> m.getAccessId() != null && m.getAccessId().startsWith("az");
    } else {
      filter = m -> m.getType().toString().equals(cloudPlatform.toString());
    }
    return accessMethods.stream().filter(filter).findFirst();
  }

  /**
   * DRS 1.5 `cloud` (CSP) value for a DrsHub request cloud platform. DrsHub's request vocabulary
   * uses the storage-type-ish `gs` for Google, whereas the DRS `cloud` field uses `gcp`.
   */
  private static String toDrsCloud(CloudPlatformEnum cloudPlatform) {
    return switch (cloudPlatform) {
      case GS -> "gcp";
      case AZURE -> "azure";
      case S3 -> "aws";
    };
  }

  public static List<AccessMethod> getAccessMethods(
      DrsObject drsResponse, DrsProvider drsProvider) {
    if (isEmpty(drsResponse)) {
      return List.of();
    }
    return drsProvider.getAccessMethodConfigs().stream()
        .flatMap(
            methodConfig ->
                drsResponse.getAccessMethods().stream()
                    .filter(m -> methodConfig.getType().getReturnedEquivalent() == m.getType()))
        .toList();
  }
}
