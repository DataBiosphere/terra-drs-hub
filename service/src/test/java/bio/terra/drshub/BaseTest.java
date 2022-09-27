package bio.terra.drshub;

import static bio.terra.drshub.controllers.DrsHubApiControllerTest.TDR_TEST_HOST;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = DrsHubApplication.class)
@ActiveProfiles({"test", "human-readable-logging"})
public abstract class BaseTest {

  @Autowired protected DrsHubConfig config;

  protected ProviderHosts getProviderHosts(String provider) {
    var drsProvider = config.getDrsProviders().get(provider);
    if (Objects.equals(provider, "terraDataRepo")) {
      return new ProviderHosts(TDR_TEST_HOST, TDR_TEST_HOST, drsProvider);
    }

    var drsHostRegex = Pattern.compile(drsProvider.getHostRegex());
    return config.getCompactIdHosts().entrySet().stream()
        .filter(h -> drsHostRegex.matcher(h.getValue()).matches())
        .findFirst()
        .map(entry -> new ProviderHosts(entry.getKey(), entry.getValue(), drsProvider))
        .get();
  }

  public void assertEmpty(Optional<?> optional) {
    assertTrue(optional.isEmpty(), "expected empty optional");
  }

  public void assertPresent(Optional<?> optional) {
    assertTrue(optional.isPresent(), "expected non-empty optional");
  }

  protected record ProviderHosts(String drsUriHost, String dnsHost, DrsProvider drsProvider) {}
}
