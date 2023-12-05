package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.MTlsConfig;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.Apache4SslUtils;
import nl.altindag.ssl.util.PemUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
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
    PoolingHttpClientConnectionManager poolingConnManager =
        new PoolingHttpClientConnectionManager();
    poolingConnManager.setMaxTotal(connectionPoolSize);
    poolingConnManager.setDefaultMaxPerRoute(connectionPoolSize);
    CloseableHttpClient httpClient =
        HttpClients.custom().setConnectionManager(poolingConnManager).build();
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(httpClient);
    return new RestTemplate(factory);
  }

  /**
   * @return a new RestTemplate backed by a pooling connection manager using mutual TLS (the client
   *     must also be authenticated)
   */
  public RestTemplate makeMTlsRestTemplateWithPooling(MTlsConfig mTlsConfig) {
    var keyManager =
        PemUtils.loadIdentityMaterial(mTlsConfig.getCertPath(), mTlsConfig.getKeyPath());
    var sslFactory = SSLFactory.builder().withIdentityMaterial(keyManager).build();
    var socketFactory = Apache4SslUtils.toSocketFactory(sslFactory);
    Registry<ConnectionSocketFactory> socketFactoryRegistry =
        RegistryBuilder.<ConnectionSocketFactory>create().register("https", socketFactory).build();

    PoolingHttpClientConnectionManager poolingConnManager =
        new PoolingHttpClientConnectionManager(socketFactoryRegistry);
    poolingConnManager.setMaxTotal(connectionPoolSize);
    poolingConnManager.setDefaultMaxPerRoute(connectionPoolSize);
    CloseableHttpClient httpClient =
        HttpClients.custom()
            .setSSLSocketFactory(socketFactory)
            .setConnectionManager(poolingConnManager)
            .build();
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(httpClient);
    return new RestTemplate(factory);
  }
}
