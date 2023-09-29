package bio.terra.drshub.tracking;

import static bio.terra.drshub.tracking.TrackingInterceptor.EVENT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.services.DrsResolutionService;
import bio.terra.drshub.services.TrackingService;
import bio.terra.drshub.util.SignedUrlTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
class TrackingInterceptorTest extends BaseTest {
  private static final String TEST_ACCESS_TOKEN = "I_am_an_access_token";
  private static final BearerToken TEST_BEARER_TOKEN = new BearerToken(TEST_ACCESS_TOKEN);
  private static final String DRS_URI = "drs://foo/object_id";
  private static final String TEST_IP_ADDRESS = "1.1.1.1";
  private static final String GOOGLE_PROJECT = "myproject";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private DrsResolutionService drsResolutionService;
  @MockBean private TrackingService trackingService;

  @BeforeEach
  void setUp() {
    SignedUrlTestUtils.setupDrsResolutionServiceMocks(
        drsResolutionService, DRS_URI, "bucket", "path", GOOGLE_PROJECT, false);
  }

  @Test
  void testHappyPathEmittingToBard() throws Exception {
    String url = "/api/v4/drs/resolve";
    postRequest(url, objectMapper.writeValueAsString(Map.of("url", DRS_URI, "fields", List.of())))
        .andExpect(status().isOk());

    verify(trackingService, times(1))
        .logEvent(
            TEST_BEARER_TOKEN,
            EVENT_NAME,
            Map.of(
                "statusCode",
                200,
                "requestUrl",
                url,
                "url",
                DRS_URI,
                "fields",
                List.of(),
                "ip",
                TEST_IP_ADDRESS));
  }

  @Test
  void testDoesNotLogOn404() throws Exception {
    postRequest(
            "/api/v4/foo",
            objectMapper.writeValueAsString(Map.of("url", DRS_URI, "fields", List.of())))
        .andExpect(status().isNotFound());
    verify(trackingService, never()).logEvent(any(BearerToken.class), anyString(), any(Map.class));
  }

  @Test
  void testDoesNotLogOnUntrackedMethods() throws Exception {
    getRequest(
            "/status", objectMapper.writeValueAsString(Map.of("url", DRS_URI, "fields", List.of())))
        .andExpect(status().isOk());
    verify(trackingService, never()).logEvent(any(BearerToken.class), anyString(), any(Map.class));
  }

  private ResultActions postRequest(String url, String requestBody) throws Exception {
    return mvc.perform(
        post(url)
            .header("authorization", "bearer " + TEST_ACCESS_TOKEN)
            .header("X-Forwarded-For", TEST_IP_ADDRESS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
  }

  private ResultActions getRequest(String url, String requestBody) throws Exception {
    return mvc.perform(
        get(url)
            .header("authorization", "bearer " + TEST_ACCESS_TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
  }
}
