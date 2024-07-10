package bio.terra.drshub.tracking;

import static bio.terra.drshub.tracking.TrackingInterceptor.EVENT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.DrsHubApplication;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.generated.model.RequestObject.CloudPlatformEnum;
import bio.terra.drshub.generated.model.ServiceName;
import bio.terra.drshub.services.DrsResolutionService;
import bio.terra.drshub.services.TrackingService;
import bio.terra.drshub.util.SignedUrlTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("Unit")
@ActiveProfiles({"test", "human-readable-logging"})
@ContextConfiguration(classes = DrsHubApplication.class)
@WebMvcTest
class TrackingInterceptorTest {
  private static final String TEST_ACCESS_TOKEN = "I_am_an_access_token";
  private static final BearerToken TEST_BEARER_TOKEN = new BearerToken(TEST_ACCESS_TOKEN);
  private static final String DRS_URI = "drs://foo/object_id";
  private static final String TEST_IP_ADDRESS = "1.1.1.1";
  private static final String GOOGLE_PROJECT = "myproject";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private DrsResolutionService drsResolutionService;
  @MockBean private TrackingService trackingService;
  @MockBean private DrsHubConfig drsHubConfig;

  @BeforeEach
  void setUp() {
    SignedUrlTestUtils.setupDrsResolutionServiceMocks(
        drsResolutionService,
        DRS_URI,
        "bucket",
        "path",
        GOOGLE_PROJECT,
        Optional.of(ServiceName.TERRA_UI),
        false);
  }

  @Test
  void testHappyPathEmittingToBard() throws Exception {
    mockBardEmissionsEnabled();

    String url = "/api/v4/drs/resolve";
    postRequest(
            url,
            objectMapper.writeValueAsString(
                Map.of("url", DRS_URI, "cloudPlatform", CloudPlatformEnum.GS, "fields", List.of())))
        .andExpect(status().isOk());

    verify(trackingService)
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
                "cloudPlatform",
                CloudPlatformEnum.GS.toString(),
                "fields",
                List.of(),
                "serviceName",
                "terra_ui"));
  }

  @Test
  void testDoesNotLogWhenBardEmissionsDisabled() throws Exception {
    String url = "/api/v4/drs/resolve";
    postRequest(
            url,
            objectMapper.writeValueAsString(
                Map.of("url", DRS_URI, "cloudPlatform", CloudPlatformEnum.GS, "fields", List.of())))
        .andExpect(status().isOk());

    verifyNoInteractions(trackingService);
  }

  @Test
  void testDoesNotLogOn404() throws Exception {
    mockBardEmissionsEnabled();
    String url = "/api/v4/drs/resolve";
    postRequest(
            url,
            objectMapper.writeValueAsString(
                Map.of(
                    "url",
                    DRS_URI + "notfound",
                    "cloudPlatform",
                    CloudPlatformEnum.GS,
                    "fields",
                    List.of())))
        .andExpect(status().isNotFound());
    verify(trackingService, never()).logEvent(any(BearerToken.class), anyString(), any(Map.class));
  }

  @Test
  void testDoesNotLogOnUntrackedMethods() throws Exception {
    mockBardEmissionsEnabled();
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
            .header("X-Terra-Service-ID", "terra_ui")
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

  private void mockBardEmissionsEnabled() {
    when(drsHubConfig.bardEventLoggingEnabled()).thenReturn(true);
  }
}
