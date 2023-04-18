package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.drshub.BaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class DrsProviderServiceTest extends BaseTest {

  @Autowired private DrsProviderService drsProviderService;

  @Test
  void testResolvesDnsHostsAndProviders() {
    for (var providerName : config.getDrsProviders().keySet()) {
      var cidProviderHost = getProviderHosts(providerName);

      var testUri = String.format("drs://%s:12345", cidProviderHost.compactUriPrefix());
      var testDnsUri = String.format("drs://%s/12345/4567", cidProviderHost.dnsHost());

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
      var testCompactUriDrs = String.format("drs://%s:12345/4567/abcd/84jdox", compactId);
      var expectedUriDrs = String.format("drs://%s/12345/4567/abcd/84jdox", hostName);
      var resolvedUriDrs = drsProviderService.getUriComponents(testCompactUriDrs);
      assertEquals(expectedUriDrs, resolvedUriDrs.toString());
    }
  }

  @Test
  void testHostnameIdResolution() {
    for (var entry : config.getCompactIdHosts().entrySet()) {
      var hostName = entry.getValue();
      var testCompactUriDrs = String.format("drs://%s/12345/4567/abcd/84jdox", hostName);
      var expectedUriDrs = String.format("drs://%s/12345/4567/abcd/84jdox", hostName);
      var resolvedUriDrs = drsProviderService.getUriComponents(testCompactUriDrs);
      assertEquals(expectedUriDrs, resolvedUriDrs.toString());
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "drs://drs.anv0:v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d",
        "drs://dg.nd1k3:123456"
      })
  void testCompactIdentifierRegexMatch(String url) {
    assertTrue(
        url.matches(DrsProviderService.compactIdRegex.toString()),
        String.format(
            "Input url: %s did not match regex: %s", url, DrsProviderService.compactIdRegex));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "drs://drs.anv0/v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d",
        "drs://abc.nd1k3:123456"
      })
  void testCompactIdentifierRegexNoMatch(String url) {
    assertFalse(
        url.matches(DrsProviderService.compactIdRegex.toString()),
        String.format(
            "Input url: %s did not match regex: %s", url, DrsProviderService.compactIdRegex));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "drs://jade.datarepo-dev.broadinstitute.org/v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d",
        "drs://www.google.com/1234/456/2315asd"
      })
  void testHostnameIdentifierRegexMatch(String url) {
    assertTrue(
        url.matches(DrsProviderService.hostNameRegex.toString()),
        String.format(
            "Input url: %s did not match regex: %s", url, DrsProviderService.compactIdRegex));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "drs://jade.datarepo-dev.broadinstitute.org:v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d",
        "drs://drs.anv0:1234/456/2315asd"
      })
  void testHostnameIdentifierRegexNoMatch(String url) {
    assertFalse(
        url.matches(DrsProviderService.hostNameRegex.toString()),
        String.format(
            "Input url: %s did not match regex: %s", url, DrsProviderService.compactIdRegex));
  }

  @Test
  void testSchemeRegexMatch() {
    var url =
        "drs://jade.datarepo-dev.broadinstitute.org/v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d";
    var urlWithoutScheme = DrsProviderService.schemeRegex.matcher(url).replaceFirst("");
    assertEquals(
        "jade.datarepo-dev.broadinstitute.org/v1_e2151834-13cd-4156-9ea2-168a1b7abf60_0761203d-d2a1-448e-8f71-9f81d80ddd9d",
        urlWithoutScheme);
  }
}
