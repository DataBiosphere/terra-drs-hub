package bio.terra.drshub.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.MTlsConfig;
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
public class DrsApiFactoryTest {

  @Mock private RestTemplateFactory restTemplateFactory;
  private DrsApiFactory drsApiFactory;
  private static final UriComponents URI_COMPONENTS =
      UriComponentsBuilder.newInstance().host("test").build();

  @BeforeEach
  void setup() {
    drsApiFactory = new DrsApiFactory(restTemplateFactory);
  }

  private DrsProvider createDrsProvider() {
    return DrsProvider.create().setName("testDrsProvider");
  }

  @Test
  void testMTlsConfigured() {
    var mTlsConfig = MTlsConfig.create().setCertPath("fake.crt").setKeyPath("fake.key");
    when(restTemplateFactory.makeMTlsRestTemplateWithPooling(mTlsConfig))
        .thenReturn(new RestTemplate());

    drsApiFactory.getApiFromUriComponents(
        URI_COMPONENTS, createDrsProvider().setMTlsConfig(mTlsConfig));

    verify(restTemplateFactory, never()).makeRestTemplateWithPooling();
  }

  @Test
  void testMTlsNotConfigured() {
    when(restTemplateFactory.makeRestTemplateWithPooling()).thenReturn(new RestTemplate());

    drsApiFactory.getApiFromUriComponents(URI_COMPONENTS, createDrsProvider());

    verify(restTemplateFactory, never()).makeMTlsRestTemplateWithPooling(any(MTlsConfig.class));
  }

  @Test
  void testIndependentApiClients() {
    var drsProvider = createDrsProvider();
    when(restTemplateFactory.makeRestTemplateWithPooling()).thenReturn(new RestTemplate());

    // The first ApiClient created for a DrsProvider will populate the RestTemplate cache
    var client1 = drsApiFactory.getApiFromUriComponents(URI_COMPONENTS, drsProvider);
    verify(restTemplateFactory).makeRestTemplateWithPooling();

    // Subsequent ApiClients created for the same DrsProvider will reuse the cached RestTemplate
    var separateApiClient =
        spy(drsApiFactory.getApiFromUriComponents(URI_COMPONENTS, drsProvider).getApiClient());
    verifyNoMoreInteractions(restTemplateFactory);

    // Setting the token or headers on an ApiClient will have no effect on ApiClients created for
    // the same DrsProvider
    client1.setBearerToken("bearer-token");
    verifyNoInteractions(separateApiClient);

    client1.setHeader("test-header", "test-value");
    verifyNoInteractions(separateApiClient);
  }
}
