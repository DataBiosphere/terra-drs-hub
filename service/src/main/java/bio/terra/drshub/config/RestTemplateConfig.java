package bio.terra.drshub.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

// See
// https://stackabuse.com/spring-boot-guide-to-resttemplate/#mutualtlscertificateverificationwithresttemplate
@Configuration
public class RestTemplateConfig {

  @Value("${application.keystore.path}")
  private String keystorePath;

  @Value("${application.keystore.type}")
  private String keystoreType;

  @Value("${application.keystore.password}")
  private String keystorePassword;

  @Value("${application.truststore.path}")
  private String truststorePath;

  @Value("${application.truststore.type}")
  private String truststoreType;

  @Value("${application.truststore.password}")
  private String truststorePassword;

  @Value("${application.protocol}")
  private String protocol;

  @Bean
  public RestTemplate mutualAuthRestTemplate()
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException,
          KeyManagementException {

    // Load Keystore
    final var keystore = KeyStore.getInstance(keystoreType);
    try (var in = new FileInputStream(keystorePath)) {
      keystore.load(in, keystorePassword.toCharArray());
    }

    // Load Truststore
    final var truststore = KeyStore.getInstance(truststoreType);
    try (var in = new FileInputStream(truststorePath)) {
      truststore.load(in, truststorePassword.toCharArray());
    }

    // Build SSLConnectionSocket to verify certificates
    final var sslSocketFactory =
        new SSLConnectionSocketFactory(
            new SSLContextBuilder()
                .loadTrustMaterial(truststore, new TrustSelfSignedStrategy())
                .setProtocol(protocol)
                .build(),
            new HostnameVerifier() {
              final HostnameVerifier hostnameVerifier =
                  HttpsURLConnection.getDefaultHostnameVerifier();

              @Override
              public boolean verify(String hostname, SSLSession session) {
                return hostnameVerifier.verify(hostname, session);
              }
            });

    var httpclient = HttpClients.custom().setSSLSocketFactory(sslSocketFactory).build();
    return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpclient));
  }
}
