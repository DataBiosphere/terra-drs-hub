package bio.terra.drshub.services;

import bio.terra.drshub.config.MTlsConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory class for creating SSL contexts with client certificate authentication. This class
 * handles loading PEM files and creating SSL contexts for mutual TLS.
 */
@Component
public class SSLContextFactory {

  /**
   * Creates an SSLContext with client certificate authentication (mutual TLS)
   *
   * @param mTlsConfig Configuration containing paths to certificate and private key files
   * @return Configured SSLContext for mutual TLS
   * @throws RuntimeException if SSL context creation fails
   */
  public SSLContext createSSLContextWithClientCert(MTlsConfig mTlsConfig) {
    try {
      // Load certificate and private key
      X509Certificate certificate = loadCertificateFromPem(mTlsConfig.getCertPath());
      PrivateKey privateKey = loadPrivateKeyFromPem(mTlsConfig.getKeyPath());

      // Create keystore with client certificate
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);
      keyStore.setKeyEntry("client", privateKey, new char[0], new Certificate[] {certificate});

      // Initialize key manager
      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, new char[0]);

      // Initialize trust manager (use default)
      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init((KeyStore) null);

      // Create SSL context
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(
          keyManagerFactory.getKeyManagers(),
          trustManagerFactory.getTrustManagers(),
          new SecureRandom());

      return sslContext;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create SSL context with client certificate", e);
    }
  }

  /**
   * Loads an X509 certificate from a PEM file
   *
   * @param certificatePath Path to the certificate PEM file
   * @return X509Certificate loaded from the file
   * @throws IOException if file reading fails
   * @throws CertificateException if certificate parsing fails
   */
  private X509Certificate loadCertificateFromPem(String certificatePath)
      throws IOException, CertificateException {
    String pemContent = Files.readString(Paths.get(certificatePath));
    String certificateData = extractPemContent(pemContent, "CERTIFICATE");

    byte[] certBytes = Base64.getDecoder().decode(certificateData);
    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

    return (X509Certificate)
        certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));
  }

  /**
   * Loads a private key from a PEM file
   *
   * @param privateKeyPath Path to the private key PEM file
   * @return PrivateKey loaded from the file
   * @throws IOException if file reading fails
   * @throws NoSuchAlgorithmException if key algorithm is not supported
   * @throws InvalidKeySpecException if key parsing fails
   */
  private PrivateKey loadPrivateKeyFromPem(String privateKeyPath)
      throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    String pemContent = Files.readString(Paths.get(privateKeyPath));
    String keyData = extractPemContent(pemContent, "PRIVATE KEY");

    byte[] keyBytes = Base64.getDecoder().decode(keyData);
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

    String[] algorithms = {"RSA", "EC", "DSA"};
    for (String algorithm : algorithms) {
      try {
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
        return keyFactory.generatePrivate(keySpec);
      } catch (InvalidKeySpecException e) {
        // Try next algorithm
      }
    }

    throw new InvalidKeySpecException("Unable to parse private key with any supported algorithm");
  }

  /**
   * Extracts the Base64 content from a PEM block
   *
   * @param pemContent The PEM file content
   * @param blockType The type of PEM block (e.g., "CERTIFICATE", "PRIVATE KEY")
   * @return Base64 decoded content of the PEM block
   * @throws IllegalArgumentException if PEM block is not found
   */
  private String extractPemContent(String pemContent, String blockType) {
    Pattern pattern =
        Pattern.compile(
            "-----BEGIN "
                + blockType
                + "-----\\s*([A-Za-z0-9+/=\\s]+)\\s*-----END "
                + blockType
                + "-----",
            Pattern.MULTILINE | Pattern.DOTALL);

    var matcher = pattern.matcher(pemContent);
    if (matcher.find()) {
      return matcher.group(1).replaceAll("\\s", "");
    }

    throw new IllegalArgumentException("No " + blockType + " block found in PEM content");
  }
}
