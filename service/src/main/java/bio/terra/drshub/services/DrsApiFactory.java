package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.models.DrsApi;
import io.github.ga4gh.drs.client.ApiClient;
import java.util.Objects;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.Apache4SslUtils;
import nl.altindag.ssl.util.PemUtils;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;

@Service
public class DrsApiFactory {

  public DrsApi getApiFromUriComponents(UriComponents uriComponents, DrsProvider drsProvider) {
    var mTlsConfig = drsProvider.getMTlsConfig();
    var drsClient =
        mTlsConfig == null
            ? new ApiClient()
            : new ApiClient(
                makeMTlsRestTemplate(mTlsConfig.getCertPath(), mTlsConfig.getKeyPath()));

    drsClient.setBasePath(
        drsClient
            .getBasePath()
            .replace("{serverURL}", Objects.requireNonNull(uriComponents.getHost())));

    return new DrsApi(drsClient);
  }

  RestTemplate makeMTlsRestTemplate(String certPath, String keyPath) {
    var keyManager = PemUtils.loadIdentityMaterial(certPath, keyPath);
    var sslFactory = SSLFactory.builder().withIdentityMaterial(keyManager).build();
    var socketFactory = Apache4SslUtils.toSocketFactory(sslFactory);
    var httpclient =
        HttpClients.custom().useSystemProperties().setSSLSocketFactory(socketFactory).build();

    return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpclient));
  }
}
