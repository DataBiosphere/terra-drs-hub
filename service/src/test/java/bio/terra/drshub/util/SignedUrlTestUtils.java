package bio.terra.drshub.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.bond.model.SaKeyObject;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.models.AnnotatedResourceMetadata;
import bio.terra.drshub.services.AuthService;
import bio.terra.drshub.services.DrsResolutionService;
import bio.terra.drshub.services.GoogleStorageService;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateCrtKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.junit.jupiter.api.Tag;

@Tag("Unit")
public class SignedUrlTestUtils {

  public static void setupSignedUrlMocks(
      AuthService authService,
      GoogleStorageService googleStorageService,
      String googleProject,
      URL url) {

    var storage = mock(Storage.class);
    var serviceAccountKey = new SaKeyObject().data("TestServiceAccountKey");

    when(authService.fetchUserServiceAccount(any(DrsProvider.class), any(BearerToken.class)))
        .thenReturn(serviceAccountKey);
    when(googleStorageService.getAuthedStorage(eq(serviceAccountKey), eq(googleProject)))
        .thenReturn(storage);
    when(storage.signUrl(
            any(BlobInfo.class),
            any(Long.class),
            any(TimeUnit.class),
            any(Storage.SignUrlOption.class)))
        .thenReturn(url);
  }

  public static void setupSignedUrlMocksForSam(
      AuthService authService,
      String googleProject,
      String bucketName,
      String objectName,
      URL url) {

    var gsPath = String.format("gs://%s/%s", bucketName, objectName);
    when(authService.getSignedUrlForBlob(any(BearerToken.class), eq(gsPath), eq(googleProject)))
        .thenReturn(url.toString());
  }

  public static void setupDrsResolutionServiceMocks(
      DrsResolutionService drsResolutionService,
      String drsUri,
      String bucketName,
      String objectName,
      String googleProject,
      boolean forceAccessUrl) {
    doReturn(
            CompletableFuture.completedFuture(
                AnnotatedResourceMetadata.builder()
                    .requestedFields(List.of())
                    .build()
                    .gsUri("gs://" + bucketName + "/" + objectName)))
        .when(drsResolutionService)
        .resolveDrsObject(
            eq(drsUri),
            any(List.class),
            any(BearerToken.class),
            eq(forceAccessUrl),
            nullable(String.class),
            nullable(String.class));
  }

  public static String generateSaKeyObjectString()
      throws NoSuchProviderException, NoSuchAlgorithmException {
    Security.addProvider(new BouncyCastleProvider());

    // Create the public and private keys
    var keyGen = KeyPairGenerator.getInstance("RSA", "BC");
    keyGen.initialize(2048);
    var pair = keyGen.genKeyPair();

    var keyId = UUID.randomUUID().toString();
    var serviceAccountCredentials =
        ServiceAccountCredentials.newBuilder()
            .setServiceAccountUser("testUser")
            .setClientEmail("test-email@domain.org")
            .setClientId("12345678")
            .setPrivateKey(pair.getPrivate())
            .setPrivateKeyId(keyId)
            .build();

    var gson =
        new GsonBuilder()
            .registerTypeAdapter(Duration.class, new DurationAdapter())
            .registerTypeAdapter(BCRSAPrivateCrtKey.class, new BCRSAPrivateCrtKeyAdapter())
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    var keyJsonTree = gson.toJsonTree(serviceAccountCredentials);
    keyJsonTree.getAsJsonObject().addProperty("type", "service_account");
    var saKeyObject = new JsonObject();
    saKeyObject.add("data", keyJsonTree);
    return gson.toJson(saKeyObject);
  }

  static class DurationAdapter extends TypeAdapter<Duration> {
    @Override
    public void write(JsonWriter out, Duration value) throws IOException {
      out.value(value.toMillis());
    }

    @Override
    public Duration read(JsonReader in) {
      throw new UnsupportedOperationException("Not implemented");
    }
  }

  static class BCRSAPrivateCrtKeyAdapter extends TypeAdapter<BCRSAPrivateCrtKey> {

    @Override
    public void write(JsonWriter out, BCRSAPrivateCrtKey value) throws IOException {
      var sw = new StringWriter();
      try (var writer = new JcaPEMWriter(sw)) {
        writer.writeObject(new JcaPKCS8Generator(value, null));
      }
      out.value(sw.toString());
    }

    @Override
    public BCRSAPrivateCrtKey read(JsonReader in) {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
}
