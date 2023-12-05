package bio.terra.drshub.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.MTlsConfig;
import io.github.ga4gh.drs.client.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Tag("Unit")
@ExtendWith(MockitoExtension.class)
class DrsApiFactoryTest {

  @Mock private RestTemplateFactory restTemplateFactory;
  @Mock private DrsApiClientFactory drsApiClientFactory;
  @Mock private ApiClient apiClient;
  @Mock private RestTemplate restTemplate;
  private DrsApiFactory drsApiFactory;
  private static final UriComponents URI_COMPONENTS =
      UriComponentsBuilder.newInstance().host("test").build();

  @BeforeEach
  void setup() {
    drsApiFactory = new DrsApiFactory(restTemplateFactory, drsApiClientFactory);

    when(drsApiClientFactory.createClient(restTemplate)).thenReturn(apiClient);
    when(apiClient.getBasePath()).thenReturn("");
  }

  private DrsProvider createDrsProvider() {
    return DrsProvider.create().setName("testDrsProvider");
  }

  @Test
  void testMTlsConfigured() {
    var mTlsConfig = MTlsConfig.create();
    when(restTemplateFactory.makeMTlsRestTemplateWithPooling(mTlsConfig)).thenReturn(restTemplate);

    var drsApi =
        drsApiFactory.getApiFromUriComponents(
            URI_COMPONENTS, createDrsProvider().setMTlsConfig(mTlsConfig));
    assertThat(drsApi.getApiClient(), is(apiClient));

    verify(drsApiClientFactory).createClient(restTemplate);
  }

  @Test
  void testMTlsNotConfigured() {
    when(restTemplateFactory.makeRestTemplateWithPooling()).thenReturn(restTemplate);

    var drsApi = drsApiFactory.getApiFromUriComponents(URI_COMPONENTS, createDrsProvider());
    assertThat(drsApi.getApiClient(), is(apiClient));

    verify(drsApiClientFactory).createClient(restTemplate);
  }

  @Test
  void testIndependentApiClients() {
    var drsProvider = createDrsProvider();

    // The first ApiClient created for a DrsProvider will populate the RestTemplate cache
    when(restTemplateFactory.makeRestTemplateWithPooling()).thenReturn(restTemplate);
    drsApiFactory.getApiFromUriComponents(URI_COMPONENTS, drsProvider);
    verify(restTemplateFactory).makeRestTemplateWithPooling();

    // Subsequent ApiClients created for the same DrsProvider will reuse the cached RestTemplate
    drsApiFactory.getApiFromUriComponents(URI_COMPONENTS, drsProvider);
    // If there was a cache miss, this would fail as the API would be called twice.
    verify(restTemplateFactory).makeRestTemplateWithPooling();

    // Force a cache miss by using a different provider name. Now we should see two calls to the
    // rest template factory.
    drsProvider.setName("another name");
    drsApiFactory.getApiFromUriComponents(URI_COMPONENTS, drsProvider);
    verify(restTemplateFactory, times(2)).makeRestTemplateWithPooling();
  }
}
