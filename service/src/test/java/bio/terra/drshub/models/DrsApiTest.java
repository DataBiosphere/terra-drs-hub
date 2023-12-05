package bio.terra.drshub.models;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ga4gh.drs.client.ApiClient;
import io.github.ga4gh.drs.client.auth.OAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("Unit")
@ExtendWith(MockitoExtension.class)
class DrsApiTest {

  @Mock private ApiClient apiClient;

  private DrsApi drsApi;

  @BeforeEach
  void setup() {
    drsApi = new DrsApi(apiClient);
  }

  @Test
  void testSetBearerToken() {
    var accessToken = "bearerToken";
    var oAuth = mock(OAuth.class);
    when(apiClient.getAuthentication("BearerAuth")).thenReturn(oAuth);

    drsApi.setBearerToken(accessToken);

    verify(oAuth).setAccessToken(accessToken);
  }

  @Test
  void testSetHeader() {
    var name = "name";
    var value = "value";

    drsApi.setHeader(name, value);

    verify(apiClient).addDefaultHeader(name, value);
  }
}
