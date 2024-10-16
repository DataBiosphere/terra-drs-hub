package bio.terra.drshub.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.DrsApi;
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

  private static final DrsProvider DRS_PROVIDER_UNAUTH =
      DrsProvider.create().setMetadataAuth(false);
  private static final DrsProvider DRS_PROVIDER_AUTH = DrsProvider.create().setMetadataAuth(true);

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
          .setMetadataAuth(true)
          .setName("test")
          .setHostRegex(".*")
          .setAccessMethodConfigs(
              new ArrayList<>(
                  List.of(
                      ProviderAccessMethodConfig.create()
                          .setType(AccessMethodConfigTypeEnum.gs)
                          .setAuth(AccessUrlAuthEnum.current_request)
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

    var actual =
        drsResolutionService.fetchObjectInfo(
            DRS_PROVIDER_AUTH, uriComponents, "drsUri", TOKEN, List.of(BEARERAUTH), TRANSACTION_ID);

    // When authorization is required, we pass the bearer token to the API.
    verify(drsApi).setBearerToken(TOKEN.getToken());
    // When RAS passports are not a supported means of authorization, we don't obtain them.
    verifyNoInteractions(authService);
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

  @Test
  void fetchObjectInfo_passport() {
    when(authService.fetchPassports(TOKEN)).thenReturn(Optional.of(PASSPORTS));
    when(drsApi.postObject(Map.of("passports", PASSPORTS), PATH)).thenReturn(DRS_OBJECT);

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
            TRANSACTION_ID);
    assertThat("signed url is properly returned", response.getUrl(), equalTo(url.toString()));
    verify(drsApi, never()).setHeader("X-Forwarded-For", ip);
    verify(drsApi, never()).setHeader("x-user-project", googleProject);
    verify(drsApi).setHeader(DrsResolutionService.TRANSACTION_ID_HEADER_NAME, TRANSACTION_ID);
  }
}
