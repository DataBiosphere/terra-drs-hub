package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.DrsHubException;
import bio.terra.drshub.util.SignedUrlTestUtils;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
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

    var drsUri = "drs://dg.4503:1234/456/2315asd";
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    var url = new URL("https", "storage.cloud.google.com", "/" + bucketName + "/" + objectName);

    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);

    var signedUrl =
        signedUrlService.getSignedUrl(
            bucketName,
            objectName,
            drsUri,
            googleProject,
            Optional.empty(),
            new BearerToken("12345"),
            "127.0.0.1");
    assertEquals(url, signedUrl);
  }

  @Test
  void testGetSignedUrlFromSam() throws MalformedURLException {

    var drsUri = "drs://drs.anv0:1234/456/2315asd";
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    var url = new URL("https", "storage.cloud.google.com", "/" + bucketName + "/" + objectName);

    SignedUrlTestUtils.setupSignedUrlMocksForSam(
        authService, googleProject, bucketName, objectName, url);

    var signedUrl =
        signedUrlService.getSignedUrl(
            bucketName,
            objectName,
            drsUri,
            googleProject,
            Optional.empty(),
            new BearerToken("12345"),
            "127.0.0.1");
    assertEquals(url, signedUrl);
  }

  @Test
  void testGetSignedUrlDataObjectUriOnly() throws Exception {

    var drsUri = "drs://dg.4503:1234/456/2315asd";
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    var url = new URL("https", "storage.cloud.google.com", "/" + bucketName + "/" + objectName);

    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);
    SignedUrlTestUtils.setupDrsResolutionServiceMocks(
        drsResolutionService, drsUri, bucketName, objectName, googleProject, true);
    var signedUrl =
        signedUrlService.getSignedUrl(
            null,
            null,
            drsUri,
            googleProject,
            Optional.empty(),
            new BearerToken("12345"),
            "127.0.0.1");
    assertEquals(url, signedUrl);
  }

  @Test
  void testFailsToParseInvalidURLsFromSam() {

    when(authService.getSignedUrlForBlob(
            any(BearerToken.class), any(String.class), any(String.class)))
        .thenReturn("not_a_valid_url");

    var drsUri = "drs://drs.anv0:1234/456/2315asd";
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    assertThrows(
        DrsHubException.class,
        () ->
            signedUrlService.getSignedUrl(
                bucketName,
                objectName,
                drsUri,
                googleProject,
                Optional.empty(),
                new BearerToken("12345"),
                "127.0.0.1"));
  }
}
