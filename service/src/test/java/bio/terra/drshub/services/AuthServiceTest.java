package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.bond.api.BondApi;
import bio.terra.bond.model.AccessTokenObject;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.models.DrsApi;
import bio.terra.drshub.models.DrsHubAuthorization;
import bio.terra.externalcreds.api.OidcApi;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.Authorizations;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.RestClientException;

class AuthServiceTest extends BaseTest {

  @Autowired private AuthService authService;
  @Autowired private DrsProviderService drsProviderService;
  @MockBean private DrsApiFactory drsApiFactory;
  @MockBean private DrsApi drsApi;
  @MockBean private BondApiFactory bondApiFactory;
  @MockBean private BondApi bondApi;
  @MockBean private ExternalCredsApiFactory externalCredsApiFactory;
  @MockBean private OidcApi oidcApi;

  @Test
  void testDrsOptionsEndpoint() {
    var expectedAuthorizations =
        new Authorizations()
            .supportedTypes(
                List.of(
                    Authorizations.SupportedTypesEnum.PASSPORTAUTH,
                    Authorizations.SupportedTypesEnum.BEARERAUTH));

    var cidProviderHost = getProviderHosts("passport");
    var testUri = String.format("drs://%s/12345", cidProviderHost.drsUriHost());

    var resolvedUri = drsProviderService.getUriComponents(testUri);

    when(drsApiFactory.getApiFromUriComponents(resolvedUri, cidProviderHost.drsProvider()))
        .thenReturn(drsApi);
    when(drsApi.optionsObject(any())).thenReturn(expectedAuthorizations);

    // Authorizations that exist should result in the Authorizations wrapped in Optional
    var authorizations =
        authService.fetchDrsAuthorizations(cidProviderHost.drsProvider(), resolvedUri);
    assertPresent(authorizations);

    // Some DRS Providers return `null` when an object isn't found, instead of a 4xx error.
    // These should be handled like the server doesn't yet support the OPTIONS endpoint
    when(drsApi.optionsObject(any())).thenReturn(null);
    authorizations = authService.fetchDrsAuthorizations(cidProviderHost.drsProvider(), resolvedUri);
    assertEmpty(authorizations);

    // A call to an options endpoint that contains an error should also be handled like
    // the provider doesn't yet support the OPTIONS endpoint.
    when(drsApi.optionsObject(any())).thenThrow(new RestClientException("Ruh roh"));
    authorizations = authService.fetchDrsAuthorizations(cidProviderHost.drsProvider(), resolvedUri);
    assertEmpty(authorizations);
  }

  @Test
  void testMappingDrsAuthorizations() {
    var expectedAuthorizations =
        new Authorizations()
            .supportedTypes(
                List.of(
                    Authorizations.SupportedTypesEnum.PASSPORTAUTH,
                    Authorizations.SupportedTypesEnum.BEARERAUTH,
                    Authorizations.SupportedTypesEnum.NONE));

    var passport = "I am a passport";
    var fencetoken = "fence_token";
    var bearerToken = "bearer_token";

    var cidProviderHost = getProviderHosts("passport");
    var testUri = String.format("drs://%s/12345", cidProviderHost.drsUriHost());

    var resolvedUri = drsProviderService.getUriComponents(testUri);

    when(drsApiFactory.getApiFromUriComponents(any(), any())).thenReturn(drsApi);
    when(drsApi.optionsObject(any())).thenReturn(expectedAuthorizations);

    when(bondApiFactory.getApi(any())).thenReturn(bondApi);
    when(bondApi.getLinkAccessToken(any())).thenReturn(new AccessTokenObject().token(fencetoken));
    when(externalCredsApiFactory.getApi(any())).thenReturn(oidcApi);
    when(oidcApi.getProviderPassport(any())).thenReturn(passport);

    List<DrsHubAuthorization> authorizations =
        authService.buildAuthorizations(
            cidProviderHost.drsProvider(), resolvedUri, new BearerToken(bearerToken));

    Set<Optional<List<String>>> secrets =
        authorizations.stream()
            .map(a -> a.getAuthForAccessMethodType().apply(AccessMethod.TypeEnum.GS))
            .collect(Collectors.toSet());

    Set<Optional<List<String>>> expected =
        Set.of(Optional.of(List.of(passport)), Optional.of(List.of(fencetoken)), Optional.empty());

    // This time, it should have the fence token, not the bearer token.
    assertEquals(expected, secrets);

    verify(bondApi).getLinkAccessToken(any());
    verify(oidcApi).getProviderPassport(any());

    // TDR should result in using the current request bearer token instead of the fence token.
    var bearerProviderHost = getProviderHosts("passportBearerFallback");
    testUri = String.format("drs://%s/12345", bearerProviderHost.drsUriHost());
    resolvedUri = drsProviderService.getUriComponents(testUri);

    authorizations =
        authService.buildAuthorizations(
            bearerProviderHost.drsProvider(), resolvedUri, new BearerToken(bearerToken));

    secrets =
        authorizations.stream()
            .map(a -> a.getAuthForAccessMethodType().apply(AccessMethod.TypeEnum.GS))
            .collect(Collectors.toSet());

    expected =
        Set.of(Optional.of(List.of(passport)), Optional.of(List.of(bearerToken)), Optional.empty());

    assertEquals(expected, secrets);

    // Make sure ECM wasn't called a second time due to the cache
    verify(oidcApi).getProviderPassport(any());
  }

  @Test
  void testNoOptionsResponseReliesOnConfigInstead() {

    var cidProviderHost = getProviderHosts("fenceTokenOnly");
    var testUri = String.format("drs://%s/12345", cidProviderHost.drsUriHost());

    var resolvedUri = drsProviderService.getUriComponents(testUri);

    when(drsApiFactory.getApiFromUriComponents(resolvedUri, cidProviderHost.drsProvider()))
        .thenReturn(drsApi);
    doThrow(new RestClientException("FUBAR")).when(drsApi).optionsObject(any());

    List<DrsHubAuthorization> authorizations =
        authService.buildAuthorizations(
            cidProviderHost.drsProvider(), resolvedUri, new BearerToken("foobar"));

    // Should only return fence_token authorizations
    assertPresent(
        authorizations.stream()
            .filter(a -> a.drsAuthType() == Authorizations.SupportedTypesEnum.BEARERAUTH)
            .findAny());
  }

  @Test
  void testPreferOptionsResultOverConfig() {
    var optionsResult =
        new Authorizations()
            .supportedTypes(List.of(Authorizations.SupportedTypesEnum.PASSPORTAUTH));

    var cidProviderHost = getProviderHosts("fenceTokenOnly");
    var testUri = String.format("drs://%s/12345", cidProviderHost.drsUriHost());

    var resolvedUri = drsProviderService.getUriComponents(testUri);

    when(drsApiFactory.getApiFromUriComponents(resolvedUri, cidProviderHost.drsProvider()))
        .thenReturn(drsApi);
    when(drsApi.optionsObject(any())).thenReturn(optionsResult);

    List<DrsHubAuthorization> authorizations =
        authService.buildAuthorizations(
            cidProviderHost.drsProvider(), resolvedUri, new BearerToken("foobar"));

    // Should not return fence_token authorizations
    assertEmpty(
        authorizations.stream()
            .filter(a -> a.drsAuthType() == Authorizations.SupportedTypesEnum.BEARERAUTH)
            .findAny());
  }
}
