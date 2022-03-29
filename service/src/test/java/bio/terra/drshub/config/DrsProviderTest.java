package bio.terra.drshub.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.BondProviderEnum;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class DrsProviderTest extends BaseTest {

  private final ProviderAccessMethodConfig testAccessMethodConfig =
      ProviderAccessMethodConfig.create()
          .setType(AccessMethodConfigTypeEnum.gs)
          .setAuth(AccessUrlAuthEnum.fence_token)
          .setFetchAccessUrl(true);

  @Test
  void testShouldFetchFenceAccessTokenReturnsTrue() {
    // returns true when there are no problems identifying the relevant configs
    var accessMethods = new ArrayList<ProviderAccessMethodConfig>();
    accessMethods.add(testAccessMethodConfig);
    var drsProvider = createRandomDrsProvider().setAccessMethodConfigs(accessMethods);

    assertTrue(drsProvider.shouldFetchFenceAccessToken(TypeEnum.GS, false, false));
  }

  @Test
  void testShouldFetchFenceAccessTokenNoBondProvider() {
    // returns false when the access methods are valid but there's no bond provider set
    var accessMethods = new ArrayList<ProviderAccessMethodConfig>();
    accessMethods.add(testAccessMethodConfig);
    var drsProvider =
        createRandomDrsProvider()
            .setAccessMethodConfigs(accessMethods)
            .setBondProvider(Optional.empty());

    assertFalse(drsProvider.shouldFetchFenceAccessToken(TypeEnum.GS, false, false));
  }

  @Test
  void testShouldFetchFenceAccessTokenNoAccessMethods() {
    // returns false when there are no access method configs
    var drsProvider = createRandomDrsProvider().setAccessMethodConfigs(new ArrayList<>());

    assertFalse(drsProvider.shouldFetchFenceAccessToken(TypeEnum.GS, false, false));
  }

  @Test
  void testShouldFetchFenceAccessTokenNoMatchingAccessMethodTypes() {
    // returns false when no access method configs have matching access method types
    var accessMethods = new ArrayList<ProviderAccessMethodConfig>();
    accessMethods.add(testAccessMethodConfig);
    var drsProvider = createRandomDrsProvider().setAccessMethodConfigs(accessMethods);

    assertFalse(drsProvider.shouldFetchFenceAccessToken(TypeEnum.FTP, false, false));
  }

  @Test
  void testShouldFetchFenceAccessTokenNoValidAccessMethods() {
    // returns false when there are no access method configs with fence auth method
    var accessMethods = new ArrayList<ProviderAccessMethodConfig>();
    accessMethods.add(testAccessMethodConfig.setAuth(AccessUrlAuthEnum.passport));
    var drsProvider = createRandomDrsProvider().setAccessMethodConfigs(accessMethods);

    assertFalse(drsProvider.shouldFetchFenceAccessToken(TypeEnum.GS, false, false));
  }

  private DrsProvider createRandomDrsProvider() {
    return DrsProvider.create()
        .setName(UUID.randomUUID().toString())
        .setHostRegex(UUID.randomUUID().toString())
        .setBondProvider(BondProviderEnum.fence)
        .setAccessMethodConfigs(new ArrayList<>());
  }
}
