package bio.terra.drshub.config;

import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.Apache4SslUtils;
import nl.altindag.ssl.util.PemUtils;
import org.apache.http.impl.client.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

// See https://sslcontext-kickstart.com/client/spring-resttemplate.html
@Configuration
public class RestTemplateConfig {

  @Bean
  public RestTemplate mutualAuthRestTemplate() {

    var keyManager =
        PemUtils.loadIdentityMaterial(
            "rendered/ras-mtls-client.crt", "rendered/ras-mtls-client.key");
    var trustManager = PemUtils.loadTrustMaterial("rendered/ras-mtls-ca.crt");

    var sslFactory =
        SSLFactory.builder()
            .withIdentityMaterial(keyManager)
            .withTrustMaterial(trustManager)
            .build();

    var socketFactory = Apache4SslUtils.toSocketFactory(sslFactory);

    var httpclient =
        HttpClients.custom().useSystemProperties().setSSLSocketFactory(socketFactory).build();
    return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpclient));
  }
}
