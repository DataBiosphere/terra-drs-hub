package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.MTlsConfig;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RestTemplateFactory {

  private final int connectionPoolSize;
  private final SSLContextFactory sslContextFactory;

  public RestTemplateFactory(DrsHubConfig drsHubConfig, SSLContextFactory sslContextFactory) {
    this.connectionPoolSize = drsHubConfig.restTemplateConnectionPoolSize();
    this.sslContextFactory = sslContextFactory;
  }

  /**
   * @return a new RestTemplate backed by a pooling connection manager
   */
  public RestTemplate makeRestTemplateWithPooling() {
    return makeRestTemplateWithPooling(null);
  }

  /**
   * @return a new RestTemplate backed by a pooling connection manager using mutual TLS (the client
   *     must also be authenticated)
   */
  public RestTemplate makeMTlsRestTemplateWithPooling(MTlsConfig mTlsConfig) {
    SSLContext sslContext = sslContextFactory.createSSLContextWithClientCert(mTlsConfig);
    SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
    return makeRestTemplateWithPooling(sslSocketFactory);
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
