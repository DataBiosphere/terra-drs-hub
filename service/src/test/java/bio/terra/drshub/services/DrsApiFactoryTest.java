package bio.terra.drshub.services;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.config.DrsHubConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

public class DrsApiFactoryTest extends BaseTest {
  @Autowired DrsApiFactory drsApiFactory;
  @Autowired private DrsHubConfig config;

  @Test
  void testMTlsConfigured() {
    var drsProvider = config.getDrsProviders().get("passport");
    var spy = Mockito.spy(drsApiFactory);

    spy.getApiFromUriComponents(
        UriComponentsBuilder.newInstance().host("test").build(), drsProvider);

    Mockito.verify(spy)
        .makeMTlsRestTemplate(
            drsProvider.getMTlsConfig().getCertPath(), drsProvider.getMTlsConfig().getKeyPath());
  }

  @Test
  void testMTlsNotConfigured() {
    var drsProvider = config.getDrsProviders().get("kidsFirst");
    var spy = Mockito.spy(drsApiFactory);

    spy.getApiFromUriComponents(
        UriComponentsBuilder.newInstance().host("test").build(), drsProvider);

    Mockito.verify(spy, Mockito.never())
        .makeMTlsRestTemplate(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
  }
}
