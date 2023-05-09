package bio.terra.drshub.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.services.AuthService;
import bio.terra.drshub.services.GoogleStorageService;
import bio.terra.drshub.util.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
public class GcsApiControllerTest extends BaseTest {

  public static final String TEST_ACCESS_TOKEN = "I_am_an_access_token";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean AuthService authService;
  @MockBean GoogleStorageService googleStorageService;

  @Test
  void testSignsUrls() throws Exception {
    var drsUri = "drs://drs.anv0:1234/456/2315asd";
    var bucketName = "my-test-bucket";
    var objectName = "my-test-folder/my-test-object.txt";
    var googleProject = "test-google-project";
    var url = new URL("https", "storage.cloud.google.com", "/" + bucketName + "/" + objectName);

    TestUtils.setupSignedUrlMocks(authService, googleStorageService, googleProject, url);

    var response =
        getSignedUrlRequest(TEST_ACCESS_TOKEN, bucketName, objectName, drsUri, googleProject);
    response.andExpect(content().string(url.toString()));
  }

  private ResultActions getSignedUrlRequest(
      String accessToken,
      String bucketName,
      String objectName,
      String drsObjectUri,
      String googleProject)
      throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "bucket",
                bucketName,
                "object",
                objectName,
                "dataObjectUri",
                drsObjectUri,
                "googleProject",
                googleProject));

    return getSignedUrlRequestRaw(accessToken, requestBody);
  }

  private ResultActions getSignedUrlRequestRaw(String accessToken, String requestBody)
      throws Exception {
    return mvc.perform(
        post("/api/v4/gcs/getSignedUrl")
            .header("authorization", "bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
  }
}
