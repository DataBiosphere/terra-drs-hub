package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.MTlsConfig;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.apache5.util.Apache5SslUtils;
import nl.altindag.ssl.pem.util.PemUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RestTemplateFactory {

  private final int connectionPoolSize;

  public RestTemplateFactory(DrsHubConfig drsHubConfig) {
    connectionPoolSize = drsHubConfig.restTemplateConnectionPoolSize();
  }

  /** @return a new RestTemplate backed by a pooling connection manager */
  public RestTemplate makeRestTemplateWithPooling() {
    return makeRestTemplateWithPooling(null);
  }

  /**
   * @return a new RestTemplate backed by a pooling connection manager using mutual TLS (the client
   *     must also be authenticated)
   */
  public RestTemplate makeMTlsRestTemplateWithPooling(MTlsConfig mTlsConfig) {
    var keyManager =
        PemUtils.loadIdentityMaterial(mTlsConfig.getCertPath(), mTlsConfig.getKeyPath());
    var sslFactory = SSLFactory.builder().withIdentityMaterial(keyManager).build();
    var socketFactory = Apache5SslUtils.toSocketFactory(sslFactory);

    return makeRestTemplateWithPooling(socketFactory);
  }

  /**
   * @return a new RestTemplate backed by a pooling connection manager with its SSL socket factory
   *     set (if specified)
   */
  private RestTemplate makeRestTemplateWithPooling(LayeredConnectionSocketFactory socketFactory) {
    var poolingConnManagerBuilder =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(connectionPoolSize)
            .setMaxConnPerRoute(connectionPoolSize);
    if (socketFactory != null) {
      poolingConnManagerBuilder.setSSLSocketFactory(socketFactory);
    }
    PoolingHttpClientConnectionManager poolingConnManager = poolingConnManagerBuilder.build();
    CloseableHttpClient httpClient =
        HttpClients.custom().setConnectionManager(poolingConnManager).build();
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(httpClient);
    return new RestTemplate(factory);
  }
}
