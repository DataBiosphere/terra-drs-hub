package bio.terra.drshub.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.models.BondProviderEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

public class DrsProviderInterfaceTest extends BaseTest {

  @MockBean private DrsProvider drsProviderMock;
  @MockBean private ProviderAccessMethodConfig providerAccessMethodConfig;

  @BeforeEach
  void generateProvider() {
    drsProviderMock = DrsProvider.create().setBondProvider(BondProviderEnum.anvil);
  }

  @Test
  void testUseAliasesForLocalizationPathReturnsFalse() throws Exception {
    assertFalse(drsProviderMock.useAliasesForLocalizationPath());
  }

  @Test
  void testGetAccessMethodByTypeReturnsCorrectType() throws Exception {
    // TODO: fix type mismatch sigh
    //    verify(drsProviderInterfaceMock.getAccessMethodByType(TypeEnum.S3), TypeEnum.S3);
  }
  //  default ProviderAccessMethodConfig getAccessMethodByType(AccessMethod.TypeEnum
  // accessMethodType) {
  //    return getAccessMethodConfigs().stream()
  //        .filter(o -> o.getType().getReturnedEquivalent() == accessMethodType)
  //        .findFirst()
  //        .orElse(null);
  //  }

}
