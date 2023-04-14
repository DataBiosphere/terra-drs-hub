package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.drshub.BaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DrsProviderServiceTest extends BaseTest {

  @Autowired private DrsProviderService drsProviderService;

  @Test
  void testResolvesDnsHostsAndProviders() {
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

  @Test
  void testMapsCompactIdsToTheirFullHosts() {
    for (var entry : config.getCompactIdHosts().entrySet()) {
      var compactId = entry.getKey();
      var hostName = entry.getValue();
      var testCompactUri = String.format("drs://%s/12345", compactId);
      var expectedUri = String.format("//%s/12345", hostName);
      var resolvedUri = drsProviderService.getUriComponents(testCompactUri);
      System.out.println("expected:" + expectedUri + "resolved: " + resolvedUri);
      assertEquals(expectedUri, resolvedUri.toString());
    }
  }

  @Test
  void testCompactIdentifierGetUriComponents() {
    var oldExpectedUrl =
        "drs://staging.theanvil.io/v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d";
    var newExpectedUrl =
        "drs://jade.datarepo-dev.broadinstitute.org/v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d";

    var newCompactUrl =
        "drs://drs.anv0:v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d";
    var oldCompactUrl =
        "drs://dg.anv0:v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d";
    assertEquals(oldExpectedUrl, drsProviderService.getUriComponents(oldCompactUrl).toUriString());

    assertEquals(newExpectedUrl, drsProviderService.getUriComponents(newCompactUrl).toUriString());
  }
}
