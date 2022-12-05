package bio.terra.drshub.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.DrsProviderService;
import io.github.ga4gh.drs.model.AccessMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DrsProviderInterfaceTest extends BaseTest {

  @Autowired private DrsProviderService drsProviderService;

  @Test
  void testGetAccessMethodByType() {
    var passportProviderHost = getProviderHosts("passportRequestFallback");
    var testUri = String.format("drs://%s/12345", passportProviderHost.drsUriHost());
    var uriComponents = drsProviderService.getUriComponents(testUri);
    DrsProvider drsProvider = drsProviderService.determineDrsProvider(uriComponents);

    assertNotNull(drsProvider.getAccessMethodByType(AccessMethod.TypeEnum.GS));
    assertNull(drsProvider.getAccessMethodByType(AccessMethod.TypeEnum.S3));
  }

  @Test
  void testShouldFetchFenceAccessToken() {
    var passportProviderHost = getProviderHosts("passportRequestFallback");
    var passportTestUri = String.format("drs://%s/12345", passportProviderHost.drsUriHost());
    var passportUriComponent = drsProviderService.getUriComponents(passportTestUri);
    DrsProvider passportDrsPorvider = drsProviderService.determineDrsProvider(passportUriComponent);

    assertFalse(
        passportDrsPorvider.shouldFetchFenceAccessToken(AccessMethod.TypeEnum.GS, false, false));

    var fenceProviderHost = getProviderHosts("fenceTokenOnly");
    var fenceTestUri = String.format("drs://%s/12345", fenceProviderHost.drsUriHost());
    var fenceUriComponent = drsProviderService.getUriComponents(fenceTestUri);
    DrsProvider fenceDrsProvider = drsProviderService.determineDrsProvider(fenceUriComponent);

    assertTrue(fenceDrsProvider.shouldFetchFenceAccessToken(AccessMethod.TypeEnum.GS, false, true));
    assertTrue(
        fenceDrsProvider.shouldFetchFenceAccessToken(AccessMethod.TypeEnum.GS, false, false));
    assertFalse(
        fenceDrsProvider.shouldFetchFenceAccessToken(AccessMethod.TypeEnum.S3, false, false));

    var fallbackProviderHost = getProviderHosts("passportFenceFallback");
    var fallbackTestUri = String.format("drs://%s/12345", fallbackProviderHost.drsUriHost());
    var fallbackUriComponent = drsProviderService.getUriComponents(fallbackTestUri);
    DrsProvider fallbackDrsProvider = drsProviderService.determineDrsProvider(fallbackUriComponent);

    assertFalse(
        fallbackDrsProvider.shouldFetchFenceAccessToken(AccessMethod.TypeEnum.GS, false, false));
    assertTrue(
        fallbackDrsProvider.shouldFetchFenceAccessToken(AccessMethod.TypeEnum.GS, true, false));
  }

  @Test
  void testShouldFetchAccessUrl() {
    var passportProviderHost = getProviderHosts("passportRequestFallback");
    var passportTestUri = String.format("drs://%s/12345", passportProviderHost.drsUriHost());
    var passportUriComponent = drsProviderService.getUriComponents(passportTestUri);
    DrsProvider passportDrsProvider = drsProviderService.determineDrsProvider(passportUriComponent);

    assertFalse(
        passportDrsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.GS, Fields.ACCESS_ID_FIELDS, false));
    assertFalse(
        passportDrsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.S3, Fields.ACCESS_ID_FIELDS, false));

    var fenceProviderHost = getProviderHosts("fenceTokenOnly");
    var fenceTestUri = String.format("drs://%s/12345", fenceProviderHost.drsUriHost());
    var fenceUriComponent = drsProviderService.getUriComponents(fenceTestUri);
    DrsProvider fenceDrsProvider = drsProviderService.determineDrsProvider(fenceUriComponent);

    assertTrue(
        fenceDrsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.GS, Fields.ACCESS_ID_FIELDS, false));
  }

  @Test
  void testShouldFailOnAccessUrlFail() {
    assertFalse(DrsProviderInterface.shouldFailOnAccessUrlFail(AccessMethod.TypeEnum.GS));
    assertTrue(DrsProviderInterface.shouldFailOnAccessUrlFail(AccessMethod.TypeEnum.S3));
  }
}
