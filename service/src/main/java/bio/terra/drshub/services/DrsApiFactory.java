package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.models.DrsApi;
import io.github.ga4gh.drs.client.ApiClient;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.util.UriComponents;

@Service
@Slf4j
public class DrsApiFactory {

  private static final Integer CONNECTION_POOL_SIZE = 500;

  public DrsApi getApiFromUriComponents(UriComponents uriComponents, DrsProvider drsProvider) {
    log.info(
        "Creating new DrsApi client for host '{}', for DRS Provider '{}'",
        uriComponents.getHost(),
        drsProvider.getName());
    var mTlsConfig = drsProvider.getMTlsConfig();
    var drsClient =
        mTlsConfig == null
            ? new ApiClient(makeRestTemplateWithPooling())
            : new ApiClient(
                makeMTlsRestTemplateWithPooling(mTlsConfig.getCertPath(), mTlsConfig.getKeyPath()));

    drsClient.setBasePath(
        drsClient
            .getBasePath()
            .replace("{serverURL}", Objects.requireNonNull(uriComponents.getHost())));

    return new DrsApi(drsClient);
  }

  RestTemplate makeRestTemplateWithPooling() {
    PoolingHttpClientConnectionManager poolingConnManager =
        new PoolingHttpClientConnectionManager();
    poolingConnManager.setMaxTotal(CONNECTION_POOL_SIZE);
    poolingConnManager.setDefaultMaxPerRoute(CONNECTION_POOL_SIZE);
    CloseableHttpClient httpClient =
        HttpClients.custom().setConnectionManager(poolingConnManager).build();
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(httpClient);
    return new RestTemplate(factory);
  }

  public RestTemplate makeMTlsRestTemplateWithPooling(String certPath, String keyPath) {
    var keyManager = PemUtils.loadIdentityMaterial(certPath, keyPath);
    var sslFactory = SSLFactory.builder().withIdentityMaterial(keyManager).build();
    var socketFactory = Apache4SslUtils.toSocketFactory(sslFactory);
    Registry<ConnectionSocketFactory> socketFactoryRegistry =
        RegistryBuilder.<ConnectionSocketFactory>create().register("https", socketFactory).build();

    PoolingHttpClientConnectionManager poolingConnManager =
        new PoolingHttpClientConnectionManager(socketFactoryRegistry);
    poolingConnManager.setMaxTotal(CONNECTION_POOL_SIZE);
    poolingConnManager.setDefaultMaxPerRoute(CONNECTION_POOL_SIZE);
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
