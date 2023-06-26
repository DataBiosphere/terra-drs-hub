package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.util.SignedUrlTestUtils;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@Tag("Unit")
public class SignedUrlServiceTest extends BaseTest {

  @MockBean private AuthService authService;
  @MockBean private GoogleStorageService googleStorageService;
  @MockBean private DrsResolutionService drsResolutionService;
  @Autowired private SignedUrlService signedUrlService;

  @Test
  void testGetSignedUrl() throws MalformedURLException {

    var drsUri = "drs://drs.anv0:1234/456/2315asd";
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    var url = new URL("https", "storage.cloud.google.com", "/" + bucketName + "/" + objectName);

    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);

    var signedUrl =
        signedUrlService.getSignedUrl(
            bucketName, objectName, drsUri, googleProject, new BearerToken("12345"), "127.0.0.1");
    assertEquals(url, signedUrl);
  }

  @Test
  void testGetSignedUrlDataObjectUriOnly() throws Exception {

    var drsUri = "drs://drs.anv0:1234/456/2315asd";
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    var url = new URL("https", "storage.cloud.google.com", "/" + bucketName + "/" + objectName);

    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);
    SignedUrlTestUtils.setupDrsResolutionServiceMocks(
        drsResolutionService, drsUri, bucketName, objectName, googleProject);
    var signedUrl =
        signedUrlService.getSignedUrl(
            null, null, drsUri, googleProject, new BearerToken("12345"), "127.0.0.1");
    assertEquals(url, signedUrl);
  }
}
