package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.externalcreds.api.FenceAccountKeyApi;
import bio.terra.externalcreds.api.OauthApi;
import bio.terra.externalcreds.api.OidcApi;
import bio.terra.externalcreds.client.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.web.client.RestTemplate;

@Tag("Unit")
@ExtendWith(MockitoExtension.class)
class ExternalCredsApiFactoryTest {

  @Mock private DrsHubConfig drsHubConfig;
  @SpyBean private ExternalCredsApiFactory externalCredsApiFactory;
  private String accessToken = "foo";
  private String baseUrl = "https://externalcreds.dsde-dev.broadinstitute.org";

  @BeforeEach
  void setup() {
    when(drsHubConfig.getExternalcredsUrl()).thenReturn(baseUrl);
    externalCredsApiFactory = spy(new ExternalCredsApiFactory(drsHubConfig));
    ApiClient client = new ApiClient(new RestTemplate());
    client.setBasePath(baseUrl);
    client.setAccessToken(accessToken);
    when(externalCredsApiFactory.getApi(accessToken)).thenReturn(client);
  }

  @Test
  void testGetOauthApiClient() {
    OauthApi client = externalCredsApiFactory.getOauthApi(accessToken);
    verifyApiClient(client.getApiClient());
  }

  @Test
  void testGetOidcApiClient() {
    OidcApi client = externalCredsApiFactory.getOidcApi(accessToken);
    verifyApiClient(client.getApiClient());
  }

  @Test
  void testGetFenceApiClient() {
    FenceAccountKeyApi client = externalCredsApiFactory.getFenceAccountKeyApi(accessToken);
    verifyApiClient(client.getApiClient());
  }

  private void verifyApiClient(ApiClient client) {
    verify(externalCredsApiFactory, times(1)).getApi(accessToken);
    assertEquals(client.getBasePath(), drsHubConfig.getExternalcredsUrl());
  }
}
