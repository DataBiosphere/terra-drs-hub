package bio.terra.drshub.controllers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.generated.model.RequestObject.CloudPlatformEnum;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.AuthService;
import bio.terra.drshub.services.DrsResolutionService;
import bio.terra.drshub.services.GoogleStorageService;
import bio.terra.drshub.util.SignedUrlTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("Unit")
@AutoConfigureMockMvc
public class GcsApiControllerTest extends BaseTest {

  public static final String TEST_ACCESS_TOKEN = "I_am_an_access_token";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean DrsResolutionService drsResolutionService;
  @MockBean AuthService authService;
  @MockBean GoogleStorageService googleStorageService;

  @Test
  void testSignsUrls() throws Exception {
    var drsUri = "drs://dg.4503:1234/456/2315asd";
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    var url = new URL("https", "storage.cloud.google.com", "/" + bucketName + "/" + objectName);

    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);

    var response =
        getSignedUrlRequest(TEST_ACCESS_TOKEN, bucketName, objectName, drsUri, googleProject);
    response.andExpect(content().string(url.toString()));
  }

  @Test
  void testSignsUrlsDrsUriOnly() throws Exception {
    var drsUri = "drs://dg.4503:1234/456/2315asd";
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    var url = new URL("https", "storage.cloud.google.com", "/" + bucketName + "/" + objectName);

    SignedUrlTestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);
    SignedUrlTestUtils.setupDrsResolutionServiceMocks(
        drsResolutionService,
        drsUri,
        bucketName,
        objectName,
        googleProject,
        Optional.empty(),
        true);

    var response = getSignedUrlRequest(TEST_ACCESS_TOKEN, null, null, drsUri, googleProject);
    response.andExpect(content().string(url.toString()));
    verify(drsResolutionService)
        .resolveDrsObject(
            eq(drsUri),
            eq(CloudPlatformEnum.GS),
            eq(Fields.CORE_FIELDS),
            eq(Optional.empty()),
            eq(new BearerToken(TEST_ACCESS_TOKEN)),
            eq(true),
            eq(null),
            eq(googleProject),
            eq(anyString()));
  }

  private ResultActions getSignedUrlRequest(
      String accessToken,
      String bucketName,
      String objectName,
      String drsObjectUri,
      String googleProject)
      throws Exception {
    var body = new HashMap<>(Map.of("dataObjectUri", drsObjectUri, "googleProject", googleProject));
    if (bucketName != null && objectName != null) {
      body.putAll(Map.of("bucket", bucketName, "object", objectName));
    }
    var requestBody = objectMapper.writeValueAsString(body);
    return getSignedUrlRequestRaw(accessToken, requestBody, googleProject);
  }

  private ResultActions getSignedUrlRequestRaw(
      String accessToken, String requestBody, String googleProject) throws Exception {
    return mvc.perform(
        post("/api/v4/gcs/getSignedUrl")
            .header("authorization", "bearer " + accessToken)
            .header("x-user-project", googleProject)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
  }
}
