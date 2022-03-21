package bio.terra.drshub.services;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.MTlsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

public class DrsApiFactoryTest extends BaseTest {
  @Autowired DrsApiFactory drsApiFactory;

  @Test
  void testMTlsConfigured() {
    var mTlsConfig = MTlsConfig.create().setCertPath("fake.crt").setKeyPath("fake.key");
    var drsProvider = DrsProvider.create().setMTlsConfig(mTlsConfig);
    var spy = spy(drsApiFactory);

    spy.getApiFromUriComponents(
        UriComponentsBuilder.newInstance().host("test").build(), drsProvider);

    verify(spy).makeMTlsRestTemplate(mTlsConfig.getCertPath(), mTlsConfig.getKeyPath());
  }

  @Test
  void testMTlsNotConfigured() {
    var drsProvider = DrsProvider.create();
    var spy = spy(drsApiFactory);

    spy.getApiFromUriComponents(
        UriComponentsBuilder.newInstance().host("test").build(), drsProvider);

    verify(spy, never()).makeMTlsRestTemplate(anyString(), anyString());
  }
}
