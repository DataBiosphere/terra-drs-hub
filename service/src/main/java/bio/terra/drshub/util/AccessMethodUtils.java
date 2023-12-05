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
      // if the cloudPlatform was not specified, return a different access method
      if (accessMethod.isEmpty() && !accessMethods.isEmpty()) {
        accessMethod = Optional.of(accessMethods.get(0));
      }
    }
    return accessMethod;
  }

  public static Optional<AccessMethod> getAccessMethodForCloud(
      List<AccessMethod> accessMethods, CloudPlatformEnum cloudPlatform) {
    Predicate<AccessMethod> filter;
    if (cloudPlatform.equals(CloudPlatformEnum.AZURE)) {
      filter = m -> m.getAccessId() != null && m.getAccessId().startsWith("az");
    } else {
      filter = m -> m.getType().toString().equals(cloudPlatform.toString());
    }
    return accessMethods.stream().filter(filter).findFirst();
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
