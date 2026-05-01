package bio.terra.drshub.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.ProviderAccessMethodConfig;
import bio.terra.drshub.logging.AuditLogEvent;
import bio.terra.drshub.logging.AuditLogger;
import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.DrsApi;
import bio.terra.drshub.models.DrsAuthEnum;
import bio.terra.drshub.models.DrsHubAuthorization;
import bio.terra.drshub.util.SignedUrlTestUtils;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.Authorizations.SupportedTypesEnum;
import io.github.ga4gh.drs.model.DrsObject;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;

@Tag("Unit")
@ExtendWith(MockitoExtension.class)
// Adding this since we have a fair amount of common stubbing code
@MockitoSettings(strictness = Strictness.LENIENT)
class DrsResolutionServiceTest {

  private DrsResolutionService drsResolutionService;

  @Mock private DrsApi drsApi;
  @Mock private UriComponents uriComponents;
  @Mock private AuthService authService;
  @Mock private GoogleStorageService googleStorageService;

  private static final String PATH = "path";

  private static final DrsProvider DRS_PROVIDER_UNAUTH = DrsProvider.create();
  private static final DrsProvider DRS_PROVIDER_AUTH =
      DrsProvider.create().setMetadataAuthType(DrsAuthEnum.current_request);

  private static final String TOKEN_VALUE = "token";
  private static final BearerToken TOKEN = new BearerToken(TOKEN_VALUE);
  private static final List<String> PASSPORTS = List.of("passport");
  private static final DrsHubAuthorization PASSPORTAUTH =
      new DrsHubAuthorization(SupportedTypesEnum.PASSPORTAUTH, null);
  private static final DrsHubAuthorization BEARERAUTH =
      new DrsHubAuthorization(
          SupportedTypesEnum.BEARERAUTH, (var e) -> Optional.of(List.of(TOKEN_VALUE)));
  private static final DrsObject DRS_OBJECT = new DrsObject().id("drs.id");

  private static final String accessId = "foo";

  private static URL url;

  private static final DrsProvider testDrsProvider =
      DrsProvider.create()
          .setMetadataAuthType(DrsAuthEnum.current_request)
          .setName("test")
          .setHostRegex(".*")
          .setAccessMethodConfigs(
              new ArrayList<>(
                  List.of(
                      ProviderAccessMethodConfig.create()
                          .setType(AccessMethodConfigTypeEnum.gs)
                          .setAuth(DrsAuthEnum.current_request)
                          .setFetchAccessUrl(true))));

  private static final String TRANSACTION_ID = UUID.randomUUID().toString();

  @BeforeEach
  void before() throws Exception {
    DrsApiFactory drsApiFactory = mock(DrsApiFactory.class);

    drsResolutionService =
        new DrsResolutionService(drsApiFactory, authService, mock(AuditLogger.class));

    when(uriComponents.getHost()).thenReturn("host.com");
    when(uriComponents.getPath()).thenReturn(PATH);
    when(drsApiFactory.getApiFromUriComponents(eq(uriComponents), any(DrsProvider.class)))
        .thenReturn(drsApi);

    url = new URL("https://storage.cloud.google.com/my-test-bucket/my/test.txt");
  }

  @Test
  void fetchObjectInfo_noMetadataAuth() {
    when(drsApi.getObject(PATH, null)).thenReturn(DRS_OBJECT);

    var actual =
        drsResolutionService.fetchObjectInfo(
            DRS_PROVIDER_UNAUTH,
            uriComponents,
            "drsUri",
            TOKEN,
            List.of(PASSPORTAUTH, BEARERAUTH),
            TRANSACTION_ID);

    // When authorization isn't required, we don't pass the bearer token to the API.
    verify(drsApi, never()).setBearerToken(any());
    // When authorization isn't required, we don't obtain RAS passports.
    verifyNoInteractions(authService);
    verify(drsApi, never()).postObject(any(), any());

    assertThat(
        "Object fetched via getObject without token when authorization not required",
        actual,
        equalTo(DRS_OBJECT));
    // Verify transaction id header is set when fetching object info
    verify(drsApi).setHeader(DrsResolutionService.TRANSACTION_ID_HEADER_NAME, TRANSACTION_ID);
  }

  @Test
  void fetchObjectInfo_passportUnsupported() {
    when(drsApi.getObject(PATH, null)).thenReturn(DRS_OBJECT);
    when(authService.getMetadataAuthBearerToken(DRS_PROVIDER_AUTH, uriComponents, TOKEN))
        .thenReturn(TOKEN.getToken());

    var actual =
        drsResolutionService.fetchObjectInfo(
            DRS_PROVIDER_AUTH, uriComponents, "drsUri", TOKEN, List.of(BEARERAUTH), TRANSACTION_ID);

    // When authorization is required, we pass the bearer token to the API.
    verify(drsApi).setBearerToken(TOKEN.getToken());
    // When RAS passports are not a supported means of authorization, we don't obtain them.
    verify(authService, never()).fetchPassports(TOKEN);
    verify(drsApi, never()).postObject(any(), any());

    assertThat(
        "Object fetched via getObject with token when passport unsupported",
        actual,
        equalTo(DRS_OBJECT));
  }

  @Test
  void fetchObjectInfo_passportFetchThrows() {
    when(authService.fetchPassports(TOKEN)).thenThrow(RuntimeException.class);
    when(drsApi.getObject(PATH, null)).thenReturn(DRS_OBJECT);
    when(authService.getMetadataAuthBearerToken(DRS_PROVIDER_AUTH, uriComponents, TOKEN))
        .thenReturn(TOKEN.getToken());

    var actual =
        drsResolutionService.fetchObjectInfo(
            DRS_PROVIDER_AUTH,
            uriComponents,
            "drsUri",
            TOKEN,
            List.of(BEARERAUTH, PASSPORTAUTH),
            TRANSACTION_ID);

    // When authorization is required, we pass the bearer token to the API.
    verify(drsApi).setBearerToken(TOKEN.getToken());
    // When fetching passports throws, we don't try to fetch the object via POST.
    verify(drsApi, never()).postObject(any(), any());

    assertThat(
        "Object fetched via getObject with token when passport fetch throws",
        actual,
        equalTo(DRS_OBJECT));
  }

  private static Stream<Arguments> fetchObjectInfo_passportUnavailable() {
    return Stream.of(Arguments.arguments(Optional.empty(), Optional.of(List.of())));
  }

  @ParameterizedTest
  @MethodSource
  void fetchObjectInfo_passportUnavailable(Optional<List<String>> passports) {
    when(authService.fetchPassports(TOKEN)).thenReturn(passports);
    when(drsApi.getObject(PATH, null)).thenReturn(DRS_OBJECT);
    when(authService.getMetadataAuthBearerToken(DRS_PROVIDER_AUTH, uriComponents, TOKEN))
        .thenReturn(TOKEN.getToken());

    var actual =
        drsResolutionService.fetchObjectInfo(
            DRS_PROVIDER_AUTH,
            uriComponents,
            "drsUri",
            TOKEN,
            List.of(BEARERAUTH, PASSPORTAUTH),
            TRANSACTION_ID);

    // When authorization is required, we pass the bearer token to the API.
    verify(drsApi).setBearerToken(TOKEN.getToken());
    // When a user has no passports, we don't try to fetch the object via POST.
    verify(drsApi, never()).postObject(any(), any());

    assertThat(
        "Object fetched via getObject with token when passport supported but not available",
        actual,
        equalTo(DRS_OBJECT));
  }

  private static Stream<Arguments> fetchObjectInfo_passport() {
    return Stream.of(
        Arguments.of(DrsAuthEnum.passport, List.of()),
        Arguments.of(DrsAuthEnum.current_request, List.of(PASSPORTAUTH)));
  }

  @ParameterizedTest
  @MethodSource
  void fetchObjectInfo_passport(DrsAuthEnum authType, List<DrsHubAuthorization> authorizations) {
    when(authService.fetchPassports(TOKEN)).thenReturn(Optional.of(PASSPORTS));
    when(drsApi.postObject(Map.of("passports", PASSPORTS), PATH)).thenReturn(DRS_OBJECT);

    var actual =
        drsResolutionService.fetchObjectInfo(
            DrsProvider.create().setMetadataAuthType(authType),
            uriComponents,
            "drsUri",
            TOKEN,
            authorizations,
            TRANSACTION_ID);

    // When passport authorization is used, bearer token is not passed to the API.
    verify(drsApi, never()).setBearerToken(TOKEN.getToken());
    // When fetching the object via POSTed passports succeeds, we don't attempt to fetch it via
    // bearer token.
    verify(drsApi, never()).getObject(any(), any());

    assertThat(
        "Object fetched via POSTed passport when passport supported and available",
        actual,
        equalTo(DRS_OBJECT));
  }

  @Test
  void fetchObjectInfo_failedPassportFallsBackToBearerToken() {
    when(authService.fetchPassports(TOKEN)).thenReturn(Optional.of(PASSPORTS));
    when(drsApi.postObject(Map.of("passports", PASSPORTS), PATH))
        .thenThrow(RestClientException.class);
    when(drsApi.getObject(PATH, null)).thenReturn(DRS_OBJECT);
    when(authService.getMetadataAuthBearerToken(DRS_PROVIDER_AUTH, uriComponents, TOKEN))
        .thenReturn(TOKEN.getToken());

    var actual =
        drsResolutionService.fetchObjectInfo(
            DRS_PROVIDER_AUTH,
            uriComponents,
            "drsUri",
            TOKEN,
            List.of(BEARERAUTH, PASSPORTAUTH),
            TRANSACTION_ID);

    // When authorization is required, we pass the bearer token to the API.
    verify(drsApi).setBearerToken(TOKEN.getToken());

    assertThat(
        "When fetching Object via POSTed passport fails, fall back to getObject with token",
        actual,
        equalTo(DRS_OBJECT));
  }

  @Test
  void testSignGoogleUrlWithRequesterPays() throws Exception {
    var ip = "test.ip";
    var googleProject = "test-google-project";
    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);
    when(drsApi.getAccessURL(PATH, accessId)).thenReturn(new AccessURL().url(url.toString()));
    var response =
        drsResolutionService.fetchDrsObjectAccessUrl(
            testDrsProvider,
            uriComponents,
            accessId,
            TypeEnum.GS,
            List.of(BEARERAUTH),
            new AuditLogEvent.Builder(),
            ip,
            googleProject,
            TOKEN,
            TRANSACTION_ID);
    assertThat(
        "google signed url is properly returned", response.getUrl(), equalTo(url.toString()));
    verify(drsApi).setHeader("x-user-project", googleProject);
  }

  @Test
  void testDrsResolutionHeadersIncludeIpAddress() throws Exception {
    var googleProject = "test-google-project";
    var ip = "test.ip";
    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);
    when(drsApi.getAccessURL(PATH, accessId)).thenReturn(new AccessURL().url(url.toString()));
    var response =
        drsResolutionService.fetchDrsObjectAccessUrl(
            testDrsProvider,
            uriComponents,
            accessId,
            TypeEnum.GS,
            List.of(BEARERAUTH),
            new AuditLogEvent.Builder(),
            ip,
            googleProject,
            TOKEN,
            TRANSACTION_ID);
    assertThat("signed url is properly returned", response.getUrl(), equalTo(url.toString()));
    verify(drsApi).setHeader("X-Forwarded-For", ip);
  }

  @Test
  void testDrsResolutionWithoutOptionalHeaders() throws Exception {
    String googleProject = null;
    String ip = null;
    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);

    when(drsApi.getAccessURL(PATH, accessId)).thenReturn(new AccessURL().url(url.toString()));
    var response =
        drsResolutionService.fetchDrsObjectAccessUrl(
            testDrsProvider,
            uriComponents,
            accessId,
            TypeEnum.GS,
            List.of(BEARERAUTH),
            new AuditLogEvent.Builder(),
            ip,
            googleProject,
            TOKEN,
            TRANSACTION_ID);
    assertThat("signed url is properly returned", response.getUrl(), equalTo(url.toString()));
    verify(drsApi, never()).setHeader("X-Forwarded-For", ip);
    verify(drsApi, never()).setHeader("x-user-project", googleProject);
    verify(drsApi).setHeader(DrsResolutionService.TRANSACTION_ID_HEADER_NAME, TRANSACTION_ID);
  }

  @Test
  void testTdrWithRequireUserProject_firstCallSucceeds() {
    // First call succeeds, so no retry needed and x-user-project header not sent
    var ip = "test.ip";
    var googleProject = "test-google-project";
    var tdrProvider = createTdrProviderWithRetryMode();

    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);
    when(drsApi.getAccessURL(PATH, accessId)).thenReturn(new AccessURL().url(url.toString()));

    var response =
        drsResolutionService.fetchDrsObjectAccessUrl(
            tdrProvider,
            uriComponents,
            accessId,
            TypeEnum.GS,
            List.of(BEARERAUTH),
            new AuditLogEvent.Builder(),
            ip,
            googleProject,
            TOKEN,
            TRANSACTION_ID);

    assertThat("signed url is properly returned", response.getUrl(), equalTo(url.toString()));
    verify(drsApi, never()).setHeader("x-user-project", googleProject);
  }

  @Test
  void testTdrWithRequireUserProject_retrySucceeds() {
    // Retry with x-user-project header after initial 400 "requireUserProject" error
    var ip = "test.ip";
    var googleProject = "test-google-project";
    var tdrProvider = createTdrProviderWithRetryMode();
    var retryApi = mock(DrsApi.class);

    DrsApiFactory drsApiFactory = mock(DrsApiFactory.class);
    when(drsApiFactory.getApiFromUriComponents(eq(uriComponents), any(DrsProvider.class)))
        .thenReturn(drsApi)
        .thenReturn(retryApi);

    drsResolutionService =
        new DrsResolutionService(drsApiFactory, authService, mock(AuditLogger.class));

    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);
    when(drsApi.getAccessURL(PATH, accessId))
        .thenThrow(
            HttpClientErrorException.create(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "BadRequest",
                org.springframework.http.HttpHeaders.EMPTY,
                "Snapshot requires an x-user-project header".getBytes(),
                null));
    when(retryApi.getAccessURL(PATH, accessId)).thenReturn(new AccessURL().url(url.toString()));

    var response =
        drsResolutionService.fetchDrsObjectAccessUrl(
            tdrProvider,
            uriComponents,
            accessId,
            TypeEnum.GS,
            List.of(BEARERAUTH),
            new AuditLogEvent.Builder(),
            ip,
            googleProject,
            TOKEN,
            TRANSACTION_ID);

    assertThat(
        "signed url is properly returned after retry", response.getUrl(), equalTo(url.toString()));
    verify(drsApi, never()).setHeader("x-user-project", googleProject);
    verify(retryApi).setHeader("x-user-project", googleProject);
    verify(retryApi).setHeader("X-Forwarded-For", ip);
    verify(retryApi).setHeader(DrsResolutionService.TRANSACTION_ID_HEADER_NAME, TRANSACTION_ID);
  }

  @Test
  void testTdrWithRequireUserProject_noGoogleProject() {
    // No retry without googleProject, even if error indicates it's needed
    var ip = "test.ip";
    String googleProject = null;
    var tdrProvider = createTdrProviderWithRetryMode();

    when(drsApi.getAccessURL(PATH, accessId))
        .thenThrow(
            HttpClientErrorException.create(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "BadRequest",
                org.springframework.http.HttpHeaders.EMPTY,
                "Snapshot requires an x-user-project header".getBytes(),
                null));

    assertThrows(
        HttpClientErrorException.BadRequest.class,
        () ->
            drsResolutionService.fetchDrsObjectAccessUrl(
                tdrProvider,
                uriComponents,
                accessId,
                TypeEnum.GS,
                List.of(BEARERAUTH),
                new AuditLogEvent.Builder(),
                ip,
                googleProject,
                TOKEN,
                TRANSACTION_ID));

    verify(drsApi, never()).setHeader(eq("x-user-project"), any());
  }

  @Test
  void testTdrWithRequireUserProject_unrelated400() {
    // Unrelated 400 error should not trigger retry
    var ip = "test.ip";
    var googleProject = "test-google-project";
    var tdrProvider = createTdrProviderWithRetryMode();

    when(drsApi.getAccessURL(PATH, accessId))
        .thenThrow(
            HttpClientErrorException.create(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "BadRequest",
                null,
                "Some other error message".getBytes(),
                null));

    assertThrows(
        HttpClientErrorException.BadRequest.class,
        () ->
            drsResolutionService.fetchDrsObjectAccessUrl(
                tdrProvider,
                uriComponents,
                accessId,
                TypeEnum.GS,
                List.of(BEARERAUTH),
                new AuditLogEvent.Builder(),
                ip,
                googleProject,
                TOKEN,
                TRANSACTION_ID));

    verify(drsApi, never()).setHeader(eq("x-user-project"), any());
  }

  @Test
  void testNonTdrProviderAlwaysForwards() throws Exception {
    // Non-TDR provider always sends x-user-project header on first call
    var ip = "test.ip";
    var googleProject = "test-google-project";

    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);
    when(drsApi.getAccessURL(PATH, accessId)).thenReturn(new AccessURL().url(url.toString()));

    var response =
        drsResolutionService.fetchDrsObjectAccessUrl(
            testDrsProvider, // This provider doesn't have requiresUserProjectOnRetry
            uriComponents,
            accessId,
            TypeEnum.GS,
            List.of(BEARERAUTH),
            new AuditLogEvent.Builder(),
            ip,
            googleProject,
            TOKEN,
            TRANSACTION_ID);

    assertThat("signed url is properly returned", response.getUrl(), equalTo(url.toString()));
    verify(drsApi).setHeader("x-user-project", googleProject);
  }

  private DrsProvider createTdrProviderWithRetryMode() {
    return DrsProvider.create()
        .setMetadataAuthType(DrsAuthEnum.current_request)
        .setName("tdr")
        .setHostRegex(".*")
        .setAccessMethodConfigs(
            new ArrayList<>(
                List.of(
                    ProviderAccessMethodConfig.create()
                        .setType(AccessMethodConfigTypeEnum.gs)
                        .setAuth(DrsAuthEnum.current_request)
                        .setFetchAccessUrl(true)
                        .setRequiresUserProjectOnRetry(true))));
  }

  private static Stream<Arguments> passportAuthWithGoogleProject() {
    return Stream.of(
        Arguments.of("test-project", true), // googleProject set, bearer token should be set
        Arguments.of(null, false) // no googleProject, bearer token should not be set
        );
  }

  @ParameterizedTest
  @MethodSource
  void passportAuthWithGoogleProject(String googleProject, boolean shouldSetBearerToken)
      throws Exception {
    var passportAuth =
        new DrsHubAuthorization(SupportedTypesEnum.PASSPORTAUTH, (var e) -> Optional.of(PASSPORTS));
    var bearerAuth =
        new DrsHubAuthorization(
            SupportedTypesEnum.BEARERAUTH, (var e) -> Optional.of(List.of(TOKEN_VALUE)));

    when(drsApi.postAccessURL(Map.of("passports", PASSPORTS), PATH, accessId))
        .thenReturn(new AccessURL().url("https://example.com"));

    var response =
        drsResolutionService.fetchDrsObjectAccessUrl(
            testDrsProvider,
            uriComponents,
            accessId,
            TypeEnum.GS,
            List.of(passportAuth, bearerAuth),
            new AuditLogEvent.Builder(),
            null,
            googleProject,
            TOKEN,
            TRANSACTION_ID);

    assertThat("access url returned", response.getUrl(), equalTo("https://example.com"));
    verify(drsApi).postAccessURL(Map.of("passports", PASSPORTS), PATH, accessId);

    if (shouldSetBearerToken) {
      verify(drsApi).setBearerToken(TOKEN_VALUE);
    } else {
      verify(drsApi, never()).setBearerToken(any());
    }
  }

  @Test
  void passportAuthWithGoogleProject_noBearerAuthInList() throws Exception {
    var googleProject = "test-project";
    var passportAuth =
        new DrsHubAuthorization(SupportedTypesEnum.PASSPORTAUTH, (var e) -> Optional.of(PASSPORTS));

    when(drsApi.postAccessURL(Map.of("passports", PASSPORTS), PATH, accessId))
        .thenReturn(new AccessURL().url("https://example.com"));

    var response =
        drsResolutionService.fetchDrsObjectAccessUrl(
            testDrsProvider,
            uriComponents,
            accessId,
            TypeEnum.GS,
            List.of(passportAuth),
            new AuditLogEvent.Builder(),
            null,
            googleProject,
            TOKEN,
            TRANSACTION_ID);

    assertThat("access url returned", response.getUrl(), equalTo("https://example.com"));
    verify(drsApi).postAccessURL(Map.of("passports", PASSPORTS), PATH, accessId);
    // With the fix, we now use the user's bearer token directly when googleProject is set
    verify(drsApi).setBearerToken(TOKEN_VALUE);
  }

  @Test
  void fetchDrsObjectAccessUrl_passportAuthWithGoogleProject_usesUserToken() throws Exception {
    // Test the full fetchDrsObjectAccessUrl flow to ensure user's bearer token is set
    var googleProject = "test-google-project";
    var ip = "test.ip";
    var passportAuth =
        new DrsHubAuthorization(SupportedTypesEnum.PASSPORTAUTH, (var e) -> Optional.of(PASSPORTS));

    when(drsApi.postAccessURL(Map.of("passports", PASSPORTS), PATH, accessId))
        .thenReturn(new AccessURL().url("https://signed-url.example.com/data"));

    var response =
        drsResolutionService.fetchDrsObjectAccessUrl(
            testDrsProvider,
            uriComponents,
            accessId,
            TypeEnum.GS,
            List.of(passportAuth),
            new AuditLogEvent.Builder(),
            ip,
            googleProject,
            TOKEN,
            TRANSACTION_ID);

    assertThat(
        "signed url returned with passport auth",
        response.getUrl(),
        equalTo("https://signed-url.example.com/data"));

    // Verify standard headers are set
    verify(drsApi).setHeader("X-Forwarded-For", ip);
    verify(drsApi).setHeader("x-user-project", googleProject);
    verify(drsApi).setHeader(DrsResolutionService.TRANSACTION_ID_HEADER_NAME, TRANSACTION_ID);

    // Verify user's bearer token is set before passport auth call
    verify(drsApi).setBearerToken(TOKEN_VALUE);
    verify(drsApi).postAccessURL(Map.of("passports", PASSPORTS), PATH, accessId);
  }
}
