package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.drshub.BaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DrsProviderServiceTest extends BaseTest {

  @Autowired private DrsProviderService drsProviderService;

  @Test
  void testResolvesdnsHostsAndProviders() {
    for (var providerName : config.getDrsProviders().keySet()) {
      var cidProviderHost = getProviderHosts(providerName);

      var testUri = String.format("drs://%s/12345", cidProviderHost.drsUriHost());
      var testDnsUri = String.format("drs://%s/12345", cidProviderHost.dnsHost());

      var resolvedUri = drsProviderService.getUriComponents(testUri);
      var resolvedDnsUri = drsProviderService.getUriComponents(testDnsUri);

      assertEquals(cidProviderHost.dnsHost(), resolvedUri.getHost());
      assertEquals(cidProviderHost.dnsHost(), resolvedDnsUri.getHost());

      var resolvedProvider = drsProviderService.determineDrsProvider(resolvedDnsUri);
      assertEquals(cidProviderHost.drsProvider(), resolvedProvider);
    }
  }
}
