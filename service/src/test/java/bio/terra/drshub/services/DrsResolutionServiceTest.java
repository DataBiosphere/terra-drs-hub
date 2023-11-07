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
import bio.terra.drshub.logging.AuditLogger;
import bio.terra.drshub.models.DrsApi;
import bio.terra.drshub.models.DrsHubAuthorization;
import io.github.ga4gh.drs.model.Authorizations.SupportedTypesEnum;
import io.github.ga4gh.drs.model.DrsObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;

@Tag("Unit")
@ExtendWith(MockitoExtension.class)
class DrsResolutionServiceTest {

  private DrsResolutionService drsResolutionService;

  @Mock private DrsApi drsApi;
  @Mock private UriComponents uriComponents;
  @Mock private AuthService authService;

  private static final String PATH = "path";

  private static final DrsProvider DRS_PROVIDER_UNAUTH =
      DrsProvider.create().setMetadataAuth(false);
  private static final DrsProvider DRS_PROVIDER_AUTH = DrsProvider.create().setMetadataAuth(true);

  private static final BearerToken TOKEN = new BearerToken("token");
  private static final List<String> PASSPORTS = List.of("passport");
  private static final DrsHubAuthorization PASSPORTAUTH =
      new DrsHubAuthorization(SupportedTypesEnum.PASSPORTAUTH, null);
  private static final DrsHubAuthorization BEARERAUTH =
      new DrsHubAuthorization(SupportedTypesEnum.BEARERAUTH, null);
  private static final DrsObject DRS_OBJECT = new DrsObject().id("drs.id");

  @BeforeEach
  void before() {
    DrsApiFactory drsApiFactory = mock(DrsApiFactory.class);

    drsResolutionService =
        new DrsResolutionService(
            drsApiFactory,
            mock(DrsProviderService.class),
            authService,
            mock(AuditLogger.class),
            mock(Executor.class));

    when(uriComponents.getHost()).thenReturn("host.com");
    when(uriComponents.getPath()).thenReturn(PATH);
    when(drsApiFactory.getApiFromUriComponents(eq(uriComponents), any(DrsProvider.class)))
        .thenReturn(drsApi);
  }

  @Test
  void fetchObjectInfo_noMetadataAuth() {
    when(drsApi.getObject(PATH, null)).thenReturn(DRS_OBJECT);

    var actual =
        drsResolutionService.fetchObjectInfo(
            DRS_PROVIDER_UNAUTH, uriComponents, "drsUri", TOKEN, List.of(PASSPORTAUTH, BEARERAUTH));

    // When authorization isn't required, we don't pass the bearer token to the API.
    verify(drsApi, never()).setBearerToken(any());
    // When authorization isn't required, we don't obtain RAS passports.
    verifyNoInteractions(authService);
    verify(drsApi, never()).postObject(any(), any());

    assertThat(
        "Object fetched via getObject without token when authorization not required",
        actual,
        equalTo(DRS_OBJECT));
  }

  @Test
  void fetchObjectInfo_passportUnsupported() {
    when(drsApi.getObject(PATH, null)).thenReturn(DRS_OBJECT);

    var actual =
        drsResolutionService.fetchObjectInfo(
            DRS_PROVIDER_AUTH, uriComponents, "drsUri", TOKEN, List.of(BEARERAUTH));

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
            DRS_PROVIDER_AUTH, uriComponents, "drsUri", TOKEN, List.of(BEARERAUTH, PASSPORTAUTH));

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
            DRS_PROVIDER_AUTH, uriComponents, "drsUri", TOKEN, List.of(BEARERAUTH, PASSPORTAUTH));

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
            DRS_PROVIDER_AUTH, uriComponents, "drsUri", TOKEN, List.of(BEARERAUTH, PASSPORTAUTH));

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
            DRS_PROVIDER_AUTH, uriComponents, "drsUri", TOKEN, List.of(BEARERAUTH, PASSPORTAUTH));

    // When authorization is required, we pass the bearer token to the API.
    verify(drsApi).setBearerToken(TOKEN.getToken());

    assertThat(
        "When fetching Object via POSTed passport fails, fall back to getObject with token",
        actual,
        equalTo(DRS_OBJECT));
  }
}
