package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import bio.terra.drshub.config.MTlsConfig;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import javax.net.ssl.SSLContext;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("Unit")
class SSLContextFactoryTest {

  private SSLContextFactory sslContextFactory;
  private MTlsConfig mockMTlsConfig;

  @TempDir Path tempDir;

  private Path certFile;
  private Path keyFile;
  private KeyPair testKeyPair;
  private X509Certificate testCertificate;

  @BeforeEach
  void setUp() throws Exception {
    // Add BouncyCastle provider for certificate generation
    Security.addProvider(new BouncyCastleProvider());

    sslContextFactory = new SSLContextFactory();
    mockMTlsConfig = mock(MTlsConfig.class);

    // Generate test key pair and certificate
    generateTestKeyPairAndCertificate();

    // Create temporary files
    certFile = tempDir.resolve("test.crt");
    keyFile = tempDir.resolve("test.key");

    // Write PEM files
    writeCertificatePem(certFile, testCertificate);
    writePrivateKeyPem(keyFile, testKeyPair.getPrivate());

    // Setup mock config
    when(mockMTlsConfig.getCertPath()).thenReturn(certFile.toString());
    when(mockMTlsConfig.getKeyPath()).thenReturn(keyFile.toString());
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
    // Given - write invalid certificate content
    Files.writeString(
        certFile, "-----BEGIN CERTIFICATE-----\nINVALID_CONTENT\n-----END CERTIFICATE-----");

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
    // Given - write invalid private key content
    Files.writeString(
        keyFile, "-----BEGIN PRIVATE KEY-----\nINVALID_CONTENT\n-----END PRIVATE KEY-----");

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
    // Given - write content without proper PEM block
    Files.writeString(certFile, "This is not a PEM certificate");

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
    // Given - write content without proper PEM block
    Files.writeString(keyFile, "This is not a PEM private key");

    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig));

    assertTrue(
        exception.getMessage().contains("Failed to create SSL context with client certificate"));
  }

  @Test
  void testCreateSSLContextWithClientCert_ECKeySupport() throws Exception {
    // Given - generate EC key pair
    KeyPairGenerator ecKeyGen = KeyPairGenerator.getInstance("EC");
    ecKeyGen.initialize(256);
    KeyPair ecKeyPair = ecKeyGen.generateKeyPair();

    // Create certificate with EC key
    X509Certificate ecCertificate = createSelfSignedCertificate(ecKeyPair, "EC");

    // Write EC PEM files
    Path ecCertFile = tempDir.resolve("ec_test.crt");
    Path ecKeyFile = tempDir.resolve("ec_test.key");

    writeCertificatePem(ecCertFile, ecCertificate);
    writePrivateKeyPem(ecKeyFile, ecKeyPair.getPrivate());

    when(mockMTlsConfig.getCertPath()).thenReturn(ecCertFile.toString());
    when(mockMTlsConfig.getKeyPath()).thenReturn(ecKeyFile.toString());

    // When
    SSLContext sslContext = sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig);

    // Then
    assertNotNull(sslContext);
    assertEquals("TLS", sslContext.getProtocol());
  }

  @Test
  void testCreateSSLContextWithClientCert_CertificateWithExtraWhitespace() throws IOException {
    // Given - write certificate with extra whitespace
    String certContent = toPemFormat(testCertificate, "CERTIFICATE");
    String certWithWhitespace = certContent.replace("\n", "\n  \t  "); // Add extra whitespace
    Files.writeString(certFile, certWithWhitespace);

    // When
    SSLContext sslContext = sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig);

    // Then
    assertNotNull(sslContext);
    assertEquals("TLS", sslContext.getProtocol());
  }

  @Test
  void testCreateSSLContextWithClientCert_PrivateKeyWithExtraWhitespace() throws IOException {
    // Given - write private key with extra whitespace
    String keyContent = toPemFormat(testKeyPair.getPrivate(), "PRIVATE KEY");
    String keyWithWhitespace = keyContent.replace("\n", "\n  \t  "); // Add extra whitespace
    Files.writeString(keyFile, keyWithWhitespace);

    // When
    SSLContext sslContext = sslContextFactory.createSSLContextWithClientCert(mockMTlsConfig);

    // Then
    assertNotNull(sslContext);
    assertEquals("TLS", sslContext.getProtocol());
  }

  private void generateTestKeyPairAndCertificate() throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    testKeyPair = keyGen.generateKeyPair();

    testCertificate = createSelfSignedCertificate(testKeyPair, "RSA");
  }

  @SuppressWarnings("deprecation") // X509V3CertificateGenerator is deprecated but needed for test
  private X509Certificate createSelfSignedCertificate(KeyPair keyPair, String algorithm)
      throws Exception {
    X500Name subject = new X500Name("CN=Test Certificate");
    BigInteger serial = BigInteger.valueOf(new SecureRandom().nextLong());
    Date notBefore = Date.from(Instant.now());
    Date notAfter = Date.from(Instant.now().plus(365, ChronoUnit.DAYS));

    SubjectPublicKeyInfo subjectPublicKeyInfo =
        SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

    X509v3CertificateBuilder certBuilder =
        new X509v3CertificateBuilder(
            subject, // issuer
            serial,
            notBefore,
            notAfter,
            subject, // subject
            subjectPublicKeyInfo);

    ContentSigner signer =
        new JcaContentSignerBuilder(getSignatureAlgorithm(algorithm))
            .setProvider("BC")
            .build(keyPair.getPrivate());

    X509CertificateHolder certHolder = certBuilder.build(signer);

    return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);
  }

  private String getSignatureAlgorithm(String algorithm) {
    switch (algorithm) {
      case "RSA":
        return "SHA256withRSA";
      case "EC":
        return "SHA256withECDSA";
      case "DSA":
        return "SHA256withDSA";
      default:
        return "SHA256with" + algorithm;
    }
  }

  private void writeCertificatePem(Path file, X509Certificate certificate) throws IOException {
    String pemContent = toPemFormat(certificate, "CERTIFICATE");
    Files.writeString(file, pemContent);
  }

  private void writePrivateKeyPem(Path file, PrivateKey privateKey) throws IOException {
    String pemContent = toPemFormat(privateKey, "PRIVATE KEY");
    Files.writeString(file, pemContent);
  }

  private String toPemFormat(Object obj, String type) throws IOException {
    byte[] encoded;
    if (obj instanceof X509Certificate) {
      try {
        encoded = ((X509Certificate) obj).getEncoded();
      } catch (Exception e) {
        throw new IOException("Failed to encode certificate", e);
      }
    } else if (obj instanceof PrivateKey) {
      encoded = ((PrivateKey) obj).getEncoded();
    } else {
      throw new IllegalArgumentException("Unsupported object type: " + obj.getClass());
    }

    String base64 = Base64.getEncoder().encodeToString(encoded);
    StringBuilder pem = new StringBuilder();
    pem.append("-----BEGIN ").append(type).append("-----\n");

    for (int i = 0; i < base64.length(); i += 64) {
      int end = Math.min(i + 64, base64.length());
      pem.append(base64, i, end).append("\n");
    }

    pem.append("-----END ").append(type).append("-----\n");
    return pem.toString();
  }
}
