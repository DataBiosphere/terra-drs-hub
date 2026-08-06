package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.DrsHubException;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.ProviderAccessMethodConfig;
import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.DrsApi;
import bio.terra.drshub.models.DrsAuthEnum;
import bio.terra.drshub.models.DrsHubAuthorization;
import bio.terra.drshub.models.ECMProviderEnum;
import bio.terra.externalcreds.api.OauthApi;
import bio.terra.externalcreds.api.OidcApi;
import bio.terra.sam.api.SamApi;
import bio.terra.sam.model.UserSignedUrlForBlobBody;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.Authorizations;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Tag("Unit")
class AuthServiceTest extends BaseTest {

  @Autowired private AuthService authService;
  @Autowired private DrsProviderService drsProviderService;
  @MockBean private DrsApiFactory drsApiFactory;
  @MockBean private DrsApi drsApi;
  @MockBean private ExternalCredsApiFactory externalCredsApiFactory;
  @MockBean private OauthApi oauthApi;
  @MockBean private OidcApi oidcApi;
  @MockBean private SamApiFactory samApiFactory;
  @MockBean private SamApi samApi;

  @Test
  void testDrsOptionsEndpoint() {
    var expectedAuthorizations =
        new Authorizations()
            .supportedTypes(
                List.of(
                    Authorizations.SupportedTypesEnum.PASSPORTAUTH,
                    Authorizations.SupportedTypesEnum.BEARERAUTH));

    var cidProviderHost = getProviderHosts("passport");
    var testUri = String.format("drs://%s:12345", cidProviderHost.compactUriPrefix());

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
    var providerAccessToken = "provider_access_token";
    var bearerToken = "bearer_token";

    var cidProviderHost = getProviderHosts("passport");
    var testUri = String.format("drs://%s:12345", cidProviderHost.compactUriPrefix());

    var resolvedUri = drsProviderService.getUriComponents(testUri);

    when(drsApiFactory.getApiFromUriComponents(any(), any())).thenReturn(drsApi);
    when(drsApi.optionsObject(any())).thenReturn(expectedAuthorizations);

    when(externalCredsApiFactory.getOauthApi(any())).thenReturn(oauthApi);
    when(oauthApi.getProviderAccessToken(any())).thenReturn(providerAccessToken);
    when(externalCredsApiFactory.getOidcApi(any())).thenReturn(oidcApi);
    when(oidcApi.getProviderPassport(any())).thenReturn(passport);

    List<DrsHubAuthorization> authorizations =
        authService.buildAuthorizations(
            cidProviderHost.drsProvider(), resolvedUri, new BearerToken(bearerToken));

    Set<Optional<List<String>>> secrets =
        authorizations.stream()
            .map(a -> a.getAuthForAccessMethodType().apply(AccessMethod.TypeEnum.GS, null))
            .collect(Collectors.toSet());

    Set<Optional<List<String>>> expected =
        Set.of(
            Optional.of(List.of(passport)),
            Optional.of(List.of(providerAccessToken)),
            Optional.empty());

    // This time, it should have the fence token, not the bearer token.
    assertEquals(expected, secrets);

    verify(oauthApi).getProviderAccessToken(any());
    verify(oidcApi).getProviderPassport(any());

    // TDR should result in using the current request bearer token instead of the fence token.
    var bearerProviderHost = getProviderHosts("passportRequestFallback");
    testUri = String.format("drs://%s:12345", bearerProviderHost.compactUriPrefix());
    resolvedUri = drsProviderService.getUriComponents(testUri);

    authorizations =
        authService.buildAuthorizations(
            bearerProviderHost.drsProvider(), resolvedUri, new BearerToken(bearerToken));

    secrets =
        authorizations.stream()
            .map(a -> a.getAuthForAccessMethodType().apply(AccessMethod.TypeEnum.GS, null))
            .collect(Collectors.toSet());

    expected =
        Set.of(Optional.of(List.of(passport)), Optional.of(List.of(bearerToken)), Optional.empty());

    assertEquals(expected, secrets);

    // Make sure ECM wasn't called a second time due to the cache
    verify(oidcApi).getProviderPassport(any());
  }

  @Test
  void testMappingDrsAuthorizations_bearerAuthUsesCloudToDisambiguateHttpsConfigs() {
    // TDR types both its GCS passport method and its Azure method `https` -- only the DRS 1.5
    // `cloud` field tells them apart. The BEARERAUTH resolution path must use it (via
    // getAccessMethodConfig), not fall through to whichever `https` config happens to be listed
    // first, or it reintroduces the CTM-613 bug class for this auth path.
    var azureConfig =
        ProviderAccessMethodConfig.create()
            .setType(AccessMethodConfigTypeEnum.https)
            .setAuth(DrsAuthEnum.current_request)
            .setFetchAccessUrl(true)
            .setCloud("azure");
    var gcpConfig =
        ProviderAccessMethodConfig.create()
            .setType(AccessMethodConfigTypeEnum.https)
            .setAuth(DrsAuthEnum.provider_access_token)
            .setFetchAccessUrl(true)
            .setCloud("gcp");
    var drsProvider =
        DrsProvider.create()
            .setName("tdr")
            .setHostRegex(".*")
            .setMetadataAuthType(DrsAuthEnum.current_request)
            .setEcmProvider(Optional.of(ECMProviderEnum.fence))
            .setAccessMethodConfigs(new ArrayList<>(List.of(azureConfig, gcpConfig)));

    var uriComponents = UriComponentsBuilder.fromUriString("drs://test-host/object-1").build();
    var bearerToken = new BearerToken("bearer-token-value");
    var providerAccessToken = "provider-access-token-value";

    when(drsApiFactory.getApiFromUriComponents(any(), any())).thenReturn(drsApi);
    when(drsApi.optionsObject(any()))
        .thenReturn(
            new Authorizations()
                .supportedTypes(List.of(Authorizations.SupportedTypesEnum.BEARERAUTH)));
    when(externalCredsApiFactory.getOauthApi(any())).thenReturn(oauthApi);
    when(oauthApi.getProviderAccessToken(any())).thenReturn(providerAccessToken);

    var authorizations = authService.buildAuthorizations(drsProvider, uriComponents, bearerToken);
    var bearerAuth =
        authorizations.stream()
            .filter(a -> a.drsAuthType() == Authorizations.SupportedTypesEnum.BEARERAUTH)
            .findFirst()
            .orElseThrow();

    // Azure config says current_request -> resolves to the caller's bearer token.
    assertEquals(
        Optional.of(List.of(bearerToken.getToken())),
        bearerAuth.getAuthForAccessMethodType().apply(AccessMethod.TypeEnum.HTTPS, "azure"));
    // GCP config says provider_access_token -> resolves to the fence-provider token from ECM.
    assertEquals(
        Optional.of(List.of(providerAccessToken)),
        bearerAuth.getAuthForAccessMethodType().apply(AccessMethod.TypeEnum.HTTPS, "gcp"));
  }

  @Test
  void testNoOptionsResponseReliesOnConfigInstead() {

    var cidProviderHost = getProviderHosts("fenceTokenOnly");
    var testUri = String.format("drs://%s:12345", cidProviderHost.compactUriPrefix());

    var resolvedUri = drsProviderService.getUriComponents(testUri);

    when(drsApiFactory.getApiFromUriComponents(resolvedUri, cidProviderHost.drsProvider()))
        .thenReturn(drsApi);
    doThrow(new RestClientException("FUBAR")).when(drsApi).optionsObject(any());

    List<DrsHubAuthorization> authorizations =
        authService.buildAuthorizations(
            cidProviderHost.drsProvider(), resolvedUri, new BearerToken("foobar"));

    // Should only return provider_access_token authorizations
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
    var testUri = String.format("drs://%s:12345", cidProviderHost.compactUriPrefix());

    var resolvedUri = drsProviderService.getUriComponents(testUri);

    when(drsApiFactory.getApiFromUriComponents(resolvedUri, cidProviderHost.drsProvider()))
        .thenReturn(drsApi);
    when(drsApi.optionsObject(any())).thenReturn(optionsResult);

    List<DrsHubAuthorization> authorizations =
        authService.buildAuthorizations(
            cidProviderHost.drsProvider(), resolvedUri, new BearerToken("foobar"));

    // Should not return provider_access_token authorizations
    assertEmpty(
        authorizations.stream()
            .filter(a -> a.drsAuthType() == Authorizations.SupportedTypesEnum.BEARERAUTH)
            .findAny());
  }

  @Test
  public void testSamSignsGsUrls() {
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var gsPath = "gs://" + bucketName + "/" + objectName;
    var googleProject = "test-google-project";
    var url = "https://storage.cloud.google.com" + "/" + bucketName + "/" + objectName;
    var bearerToken = new BearerToken("12345");

    when(samApiFactory.getApi(eq(bearerToken))).thenReturn(samApi);
    var body = new UserSignedUrlForBlobBody().gsPath(gsPath).requesterPaysProject(googleProject);
    when(samApi.signedUrlForBlob(eq(body))).thenReturn("\"" + url + "\"");

    var signedUrl = authService.getSignedUrlForBlob(bearerToken, gsPath, googleProject);
    assertEquals(url, signedUrl);
  }

  @Test
  public void testGetMetadataAuthBearerTokenForPassport() {
    var drsProvider =
        DrsProvider.create()
            .setMetadataAuthType(DrsAuthEnum.passport)
            .setName("name")
            .setHostRegex(".*")
            .setAccessMethodConfigs(new ArrayList<>());
    var uriComponents = UriComponentsBuilder.newInstance().build();
    assertThrows(
        DrsHubException.class,
        () ->
            authService.getMetadataAuthBearerToken(
                drsProvider, uriComponents, new BearerToken("")));
  }

  @Test
  public void testGetMetadataAuthBearerTokenForCurrentRequest() {
    var drsProvider =
        DrsProvider.create()
            .setMetadataAuthType(DrsAuthEnum.current_request)
            .setName("name")
            .setHostRegex(".*")
            .setAccessMethodConfigs(new ArrayList<>());
    var uriComponents = UriComponentsBuilder.newInstance().build();
    var token = UUID.randomUUID().toString();
    var result =
        authService.getMetadataAuthBearerToken(drsProvider, uriComponents, new BearerToken(token));
    assertEquals(token, result);
  }

  @Test
  public void testGetMetadataAuthBearerTokenForProviderAccessToken() {
    var drsProvider =
        DrsProvider.create()
            .setMetadataAuthType(DrsAuthEnum.provider_access_token)
            .setName("name")
            .setHostRegex(".*")
            .setAccessMethodConfigs(new ArrayList<>())
            .setEcmProvider(ECMProviderEnum.sage);
    var uriComponents = UriComponentsBuilder.newInstance().build();
    var token = UUID.randomUUID().toString();
    when(externalCredsApiFactory.getOauthApi(any())).thenReturn(oauthApi);
    when(oauthApi.getProviderAccessToken(any())).thenReturn(token);
    var result =
        authService.getMetadataAuthBearerToken(
            drsProvider, uriComponents, new BearerToken("not this token"));
    assertEquals(token, result);
  }
}
