package bio.terra.drshub.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.DrsAuthEnum;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.DrsProviderService;
import io.github.ga4gh.drs.model.AccessMethod;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("Unit")
class DrsProviderInterfaceTest extends BaseTest {

  @Autowired private DrsProviderService drsProviderService;

  @Test
  void testGetAccessMethodByType() {
    var passportProviderHost = getProviderHosts("passportRequestFallback");
    var testUri = String.format("drs://%s:12345", passportProviderHost.compactUriPrefix());
    var uriComponents = drsProviderService.getUriComponents(testUri);
    DrsProvider drsProvider = drsProviderService.determineDrsProvider(uriComponents);

    assertNotNull(drsProvider.getAccessMethodByType(AccessMethod.TypeEnum.GS));
    assertNull(drsProvider.getAccessMethodByType(AccessMethod.TypeEnum.S3));
  }

  @Test
  void testShouldFetchAccessUrl() {
    var passportProviderHost = getProviderHosts("passportRequestFallback");
    var passportTestUri = String.format("drs://%s:12345", passportProviderHost.compactUriPrefix());
    var passportUriComponent = drsProviderService.getUriComponents(passportTestUri);
    DrsProvider passportDrsProvider = drsProviderService.determineDrsProvider(passportUriComponent);

    assertFalse(
        passportDrsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.GS, null, Fields.ACCESS_URL_FIELDS, false));
    assertFalse(
        passportDrsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.S3, null, Fields.ACCESS_URL_FIELDS, false));

    var fenceProviderHost = getProviderHosts("fenceTokenOnly");
    var fenceTestUri = String.format("drs://%s:12345", fenceProviderHost.compactUriPrefix());
    var fenceUriComponent = drsProviderService.getUriComponents(fenceTestUri);
    DrsProvider fenceDrsProvider = drsProviderService.determineDrsProvider(fenceUriComponent);

    assertTrue(
        fenceDrsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.GS, null, Fields.ACCESS_URL_FIELDS, false));
  }

  @Test
  void testShouldFetchAccessUrl_disambiguatesByCloud() {
    // Two `https`-typed configs (GCS passport + Azure) with different `fetchAccessUrl` settings.
    // Only `cloud` can tell them apart -- type-only matching would pick whichever is listed first
    // regardless of which cloud the resolved access method actually belongs to.
    var azureConfig = createTestAccessMethodConfig(AccessMethodConfigTypeEnum.https, "azure");
    azureConfig.setFetchAccessUrl(false);
    var gcpConfig = createTestAccessMethodConfig(AccessMethodConfigTypeEnum.https, "gcp");
    var drsProvider =
        DrsProvider.create()
            .setName("tdr")
            .setHostRegex(".*")
            .setMetadataAuthType(DrsAuthEnum.current_request)
            .setAccessMethodConfigs(new ArrayList<>(List.of(azureConfig, gcpConfig)));

    assertFalse(
        drsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.HTTPS, "azure", Fields.ACCESS_URL_FIELDS, false));
    assertTrue(
        drsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.HTTPS, "gcp", Fields.ACCESS_URL_FIELDS, false));
  }

  @Test
  void testShouldFailOnAccessUrlFail() {
    assertFalse(DrsProviderInterface.shouldFailOnAccessUrlFail(AccessMethod.TypeEnum.GS));
    assertTrue(DrsProviderInterface.shouldFailOnAccessUrlFail(AccessMethod.TypeEnum.S3));
  }

  @Test
  void testGetAccessMethodConfig_selectsByTypeAndCloud() {
    // TDR types both its GCS passport method and its Azure method `https` -- only the DRS 1.5
    // `cloud` field disambiguates them, so each needs its own cloud-annotated config.
    var azureHttpsConfig = createTestAccessMethodConfig(AccessMethodConfigTypeEnum.https, "azure");
    var gcpHttpsConfig = createTestAccessMethodConfig(AccessMethodConfigTypeEnum.https, "gcp");
    var drsProvider =
        DrsProvider.create()
            .setName("tdr")
            .setHostRegex(".*")
            .setMetadataAuthType(DrsAuthEnum.current_request)
            .setAccessMethodConfigs(new ArrayList<>(List.of(azureHttpsConfig, gcpHttpsConfig)));

    assertThat(
        drsProvider.getAccessMethodConfig(AccessMethod.TypeEnum.HTTPS, "gcp"),
        equalTo(gcpHttpsConfig));
    assertThat(
        drsProvider.getAccessMethodConfig(AccessMethod.TypeEnum.HTTPS, "azure"),
        equalTo(azureHttpsConfig));
  }

  @Test
  void testGetAccessMethodConfig_fallsBackToTypeOnlyWhenCloudAbsentOrUnmatched() {
    // Only one `https` config, and it carries no `cloud` -- i.e. a provider not yet emitting DRS
    // 1.5. Selection must fall back to the legacy type-only match, unchanged.
    var httpsConfig = createTestAccessMethodConfig(AccessMethodConfigTypeEnum.https, null);
    var drsProvider =
        DrsProvider.create()
            .setName("bdc")
            .setHostRegex(".*")
            .setMetadataAuthType(DrsAuthEnum.current_request)
            .setAccessMethodConfigs(new ArrayList<>(List.of(httpsConfig)));

    assertThat(
        drsProvider.getAccessMethodConfig(AccessMethod.TypeEnum.HTTPS, null), equalTo(httpsConfig));
    // An access method `cloud` that matches no config also falls back to type-only.
    assertThat(
        drsProvider.getAccessMethodConfig(AccessMethod.TypeEnum.HTTPS, "aws"),
        equalTo(httpsConfig));
  }

  private static ProviderAccessMethodConfig createTestAccessMethodConfig(
      AccessMethodConfigTypeEnum type, String cloud) {
    var config =
        ProviderAccessMethodConfig.create()
            .setType(type)
            .setAuth(DrsAuthEnum.current_request)
            .setFetchAccessUrl(true)
            .setSupportsUserProject(true);
    return cloud == null ? config : config.setCloud(cloud);
  }
}
