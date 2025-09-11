package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.drshub.config.MTlsConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("Unit")
class SSLContextFactoryTest {

  private static final String FAKE_CERT_PATH = "src/test/resources/fake.crt";
  private static final String FAKE_KEY_PATH = "src/test/resources/fake.key";

  private SSLContextFactory sslContextFactory;
  private MTlsConfig mockMTlsConfig;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    sslContextFactory = new SSLContextFactory();
    mockMTlsConfig = mock(MTlsConfig.class);

    // Setup mock config to use fake certificate files directly
    when(mockMTlsConfig.getCertPath()).thenReturn(FAKE_CERT_PATH);
    when(mockMTlsConfig.getKeyPath()).thenReturn(FAKE_KEY_PATH);
  }

  @Test
  void testCreateSSLContextWithClientCert_Success() {
    // When
    SSLContext sslContext = sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig);

    // Then
    assertNotNull(sslContext);
    assertEquals("TLS", sslContext.getProtocol());
  }

  @Test
  void testCreateSSLContextWithClientCert_InvalidCertificatePath() {
    // Given
    when(mockMTlsConfig.getCertPath()).thenReturn("/nonexistent/cert.pem");

    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig));

    assertTrue(
        exception.getMessage().contains("Failed to create SSL context with client certificate"));
  }

  @Test
  void testCreateSSLContextWithClientCert_InvalidPrivateKeyPath() {
    // Given
    when(mockMTlsConfig.getKeyPath()).thenReturn("/nonexistent/key.pem");

    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig));

    assertTrue(
        exception.getMessage().contains("Failed to create SSL context with client certificate"));
  }

  @Test
  void testCreateSSLContextWithClientCert_InvalidCertificateContent() throws IOException {
    // Given - write invalid certificate content to temporary file
    Path certFile = tempDir.resolve("invalid.crt");
    Files.writeString(
        certFile, "-----BEGIN CERTIFICATE-----\nINVALID_CONTENT\n-----END CERTIFICATE-----");
    when(mockMTlsConfig.getCertPath()).thenReturn(certFile.toString());

    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig));

    assertTrue(
        exception.getMessage().contains("Failed to create SSL context with client certificate"));
  }

  @Test
  void testCreateSSLContextWithClientCert_InvalidPrivateKeyContent() throws IOException {
    // Given - write invalid private key content to temporary file
    Path keyFile = tempDir.resolve("invalid.key");
    Files.writeString(
        keyFile, "-----BEGIN PRIVATE KEY-----\nINVALID_CONTENT\n-----END PRIVATE KEY-----");
    when(mockMTlsConfig.getKeyPath()).thenReturn(keyFile.toString());

    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig));

    assertTrue(
        exception.getMessage().contains("Failed to create SSL context with client certificate"));
  }

  @Test
  void testCreateSSLContextWithClientCert_MissingCertificateBlock() throws IOException {
    // Given - write content without proper PEM block to temporary file
    Path certFile = tempDir.resolve("missing_block.crt");
    Files.writeString(certFile, "This is not a PEM certificate");
    when(mockMTlsConfig.getCertPath()).thenReturn(certFile.toString());

    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig));

    assertTrue(
        exception.getMessage().contains("Failed to create SSL context with client certificate"));
  }

  @Test
  void testCreateSSLContextWithClientCert_MissingPrivateKeyBlock() throws IOException {
    // Given - write content without proper PEM block to temporary file
    Path keyFile = tempDir.resolve("missing_block.key");
    Files.writeString(keyFile, "This is not a PEM private key");
    when(mockMTlsConfig.getKeyPath()).thenReturn(keyFile.toString());

    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig));

    assertTrue(
        exception.getMessage().contains("Failed to create SSL context with client certificate"));
  }

  @Test
  void testCreateSSLContextWithClientCert_CertificateWithExtraWhitespace() throws IOException {
    // Given - read fake certificate and add extra whitespace
    String fakeCertContent = Files.readString(Path.of(FAKE_CERT_PATH));
    String certWithWhitespace = fakeCertContent.replace("\n", "\n  \t  "); // Add extra whitespace
    Path certFile = tempDir.resolve("whitespace.crt");
    Files.writeString(certFile, certWithWhitespace);
    when(mockMTlsConfig.getCertPath()).thenReturn(certFile.toString());

    // When
    SSLContext sslContext = sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig);

    // Then
    assertNotNull(sslContext);
    assertEquals("TLS", sslContext.getProtocol());
  }

  @Test
  void testCreateSSLContextWithClientCert_PrivateKeyWithExtraWhitespace() throws IOException {
    // Given - read fake private key and add extra whitespace
    String fakeKeyContent = Files.readString(Path.of(FAKE_KEY_PATH));
    String keyWithWhitespace = fakeKeyContent.replace("\n", "\n  \t  "); // Add extra whitespace
    Path keyFile = tempDir.resolve("whitespace.key");
    Files.writeString(keyFile, keyWithWhitespace);
    when(mockMTlsConfig.getKeyPath()).thenReturn(keyFile.toString());

    // When
    SSLContext sslContext = sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig);

    // Then
    assertNotNull(sslContext);
    assertEquals("TLS", sslContext.getProtocol());
  }
}
