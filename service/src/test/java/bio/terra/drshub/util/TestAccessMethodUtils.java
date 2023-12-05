package bio.terra.drshub.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.ProviderAccessMethodConfig;
import bio.terra.drshub.generated.model.RequestObject.CloudPlatformEnum;
import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import io.github.ga4gh.drs.model.DrsObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("Unit")
public class TestAccessMethodUtils {
  static AccessMethod gsAccessMethod = new AccessMethod().accessId("a").type(TypeEnum.GS);
  static AccessMethod azureAccessMethod =
      new AccessMethod().accessId("az-" + UUID.randomUUID()).type(TypeEnum.HTTPS);
  static AccessMethod s3AccessMethod =
      new AccessMethod().accessId(UUID.randomUUID().toString()).type(TypeEnum.S3);
  static List<AccessMethod> accessMethods =
      List.of(gsAccessMethod, azureAccessMethod, s3AccessMethod);

  ProviderAccessMethodConfig gsAccessMethodConfig =
      createTestAccessMethodConfig(AccessMethodConfigTypeEnum.gs);
  ProviderAccessMethodConfig httpsAccessMethodConfig =
      createTestAccessMethodConfig(AccessMethodConfigTypeEnum.https);
  ArrayList<ProviderAccessMethodConfig> accessMethodConfigs =
      new ArrayList<>(List.of(gsAccessMethodConfig, httpsAccessMethodConfig));
  DrsProvider drsProvider = DrsProvider.create().setAccessMethodConfigs(accessMethodConfigs);
  DrsObject drsObject = new DrsObject().accessMethods(accessMethods);

  @Test
  void testGetAccessMethodReturnsPreferredCloud() {
    Optional<AccessMethod> accessMethod =
        AccessMethodUtils.getAccessMethod(drsObject, drsProvider, CloudPlatformEnum.AZURE);
    assertThat(accessMethod.get(), equalTo(azureAccessMethod));
  }

  @Test
  void testGetAccessMethodReturnsFallback() {
    ArrayList<ProviderAccessMethodConfig> accessMethodConfigs =
        new ArrayList<>(List.of(gsAccessMethodConfig));
    DrsProvider drsProvider = DrsProvider.create().setAccessMethodConfigs(accessMethodConfigs);
    Optional<AccessMethod> accessMethod =
        AccessMethodUtils.getAccessMethod(drsObject, drsProvider, CloudPlatformEnum.AZURE);
    assertThat(accessMethod.get(), equalTo(gsAccessMethod));
  }

  @Test
  void testGetAccessMethodNoCloudPreference() {
    Optional<AccessMethod> accessMethod =
        AccessMethodUtils.getAccessMethod(drsObject, drsProvider, null);
    assertThat(accessMethod.get(), equalTo(gsAccessMethod));
  }

  private static Stream<Arguments> testGetAccessMethodForCloud() {
    return Stream.of(
        Arguments.of(CloudPlatformEnum.GS, gsAccessMethod),
        Arguments.of(CloudPlatformEnum.AZURE, azureAccessMethod),
        Arguments.of(CloudPlatformEnum.S3, s3AccessMethod));
  }

  @ParameterizedTest
  @MethodSource
  void testGetAccessMethodForCloud(
      CloudPlatformEnum cloudPlatform, AccessMethod expectedAccessMethod) {
    Optional<AccessMethod> accessMethod =
        AccessMethodUtils.getAccessMethodForCloud(accessMethods, cloudPlatform);
    assertThat(accessMethod.get(), equalTo(expectedAccessMethod));
  }

  @Test
  void testGetAccessMethods() {
    assertThat(
        AccessMethodUtils.getAccessMethods(drsObject, drsProvider),
        equalTo(List.of(gsAccessMethod, azureAccessMethod)));
  }

  @Test
  void testGetAccessMethodsNoDrsResponse() {
    DrsProvider drsProvider = DrsProvider.create();
    assertThat(AccessMethodUtils.getAccessMethods(null, drsProvider), equalTo(List.of()));
  }

  private ProviderAccessMethodConfig createTestAccessMethodConfig(AccessMethodConfigTypeEnum type) {
    return ProviderAccessMethodConfig.create()
        .setType(type)
        .setAuth(AccessUrlAuthEnum.fence_token)
        .setFetchAccessUrl(true);
  }
}
