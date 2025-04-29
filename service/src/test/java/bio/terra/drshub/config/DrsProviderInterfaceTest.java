package bio.terra.drshub.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.DrsProviderService;
import io.github.ga4gh.drs.model.AccessMethod;
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
            AccessMethod.TypeEnum.GS, Fields.ACCESS_URL_FIELDS, false));
    assertFalse(
        passportDrsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.S3, Fields.ACCESS_URL_FIELDS, false));

    var fenceProviderHost = getProviderHosts("fenceTokenOnly");
    var fenceTestUri = String.format("drs://%s:12345", fenceProviderHost.compactUriPrefix());
    var fenceUriComponent = drsProviderService.getUriComponents(fenceTestUri);
    DrsProvider fenceDrsProvider = drsProviderService.determineDrsProvider(fenceUriComponent);

    assertTrue(
        fenceDrsProvider.shouldFetchAccessUrl(
            AccessMethod.TypeEnum.GS, Fields.ACCESS_URL_FIELDS, false));
  }

  @Test
  void testShouldFailOnAccessUrlFail() {
    assertFalse(DrsProviderInterface.shouldFailOnAccessUrlFail(AccessMethod.TypeEnum.GS));
    assertTrue(DrsProviderInterface.shouldFailOnAccessUrlFail(AccessMethod.TypeEnum.S3));
  }
}
