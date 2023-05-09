package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.bond.model.SaKeyObject;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.config.DrsProvider;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

public class SignedUrlServiceTest extends BaseTest {

  @SpyBean private DrsProviderService drsProviderService;
  @MockBean private AuthService authService;
  @MockBean private GoogleStorageService googleStorageService;
  @Mock private Storage storage;

  @Autowired private SignedUrlService signedUrlService;

  @Test
  void testGetSignedUrl() throws MalformedURLException {

    var drsUri = "drs://drs.anv0:1234/456/2315asd";
    var serviceAccountKey = new SaKeyObject().data("TestServiceAccountKey");
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    var url = new URL("https", "storage.cloud.google.com", "/" + bucketName + "/" + objectName);

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

    var signedUrl =
        signedUrlService.getSignedUrl(
            bucketName, objectName, drsUri, googleProject, new BearerToken("12345"), "127.0.0.1");
    assertEquals(url, signedUrl);
  }
}
