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
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.ProviderAccessMethodConfig;
import bio.terra.drshub.generated.model.RequestObject.CloudPlatformEnum;
import bio.terra.drshub.generated.model.ServiceName;
import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.AnnotatedResourceMetadata;
import bio.terra.drshub.models.DrsMetadata;
import bio.terra.drshub.services.DrsResolutionService;
import bio.terra.drshub.services.TrackingService;
import bio.terra.drshub.util.SignedUrlTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ga4gh.drs.model.AccessURL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
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
  private static final String REQUEST_URL = "/api/v4/drs/resolve";
  private static final String TEST_ACCESS_TOKEN = "I_am_an_access_token";
  private static final BearerToken TEST_BEARER_TOKEN = new BearerToken(TEST_ACCESS_TOKEN);
  private static final String DRS_URI =
      String.format(
          "drs://jade.datarepo-dev.broadinstitute.org/v1_%s/%s",
          UUID.randomUUID(), UUID.randomUUID());
  private static final String TEST_IP_ADDRESS = "1.1.1.1";
  private static final String GOOGLE_PROJECT = "myproject";

  private static String TRANSACTION_ID = UUID.randomUUID().toString();

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserLoggingMetrics userLoggingMetrics;
  @MockBean private DrsResolutionService drsResolutionService;
  @MockBean private TrackingService trackingService;
  @MockBean private DrsHubConfig drsHubConfig;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    userLoggingMetrics.get().clear();
    mockProviders();
    when(drsResolutionService.getTransactionId()).thenReturn(TRANSACTION_ID);
    var excludeServiceName = testInfo.getTags().contains("noServiceNameEmitted");
    Optional<ServiceName> serviceName =
        excludeServiceName ? Optional.empty() : Optional.of(ServiceName.TERRA_UI);

    SignedUrlTestUtils.setupDrsResolutionServiceMocks(
        drsResolutionService, DRS_URI, "bucket", "path", GOOGLE_PROJECT, serviceName, false);
  }

  @Test
  void testHappyPathEmittingToBard() throws Exception {
    mockBardEmissionsEnabled();

    postRequest(
            REQUEST_URL,
            objectMapper.writeValueAsString(
                Map.of("url", DRS_URI, "cloudPlatform", CloudPlatformEnum.GS, "fields", List.of())))
        .andExpect(status().isOk());

    verify(trackingService)
        .logEvent(TEST_BEARER_TOKEN, EVENT_NAME, expectedBardProperties(List.of(), null));
  }

  @Test
  @Tag("noServiceNameEmitted")
  void testHappyPathEmittingToBardNoServiceName() throws Exception {
    mockBardEmissionsEnabled();

    mvc.perform(
            post(REQUEST_URL)
                .header("authorization", "bearer " + TEST_ACCESS_TOKEN)
                .header("X-Forwarded-For", TEST_IP_ADDRESS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "url",
                            DRS_URI,
                            "cloudPlatform",
                            CloudPlatformEnum.GS,
                            "fields",
                            List.of()))))
        .andExpect(status().isOk());

    var expectedProperties = expectedBardProperties(List.of(), null);
    expectedProperties.remove("serviceName");
    verify(trackingService).logEvent(TEST_BEARER_TOKEN, EVENT_NAME, expectedProperties);
  }

  @Test
  @Tag("noServiceNameEmitted")
  void testHappyPathEmittingToBardBadServiceName() throws Exception {
    mockBardEmissionsEnabled();

    mvc.perform(
            post(REQUEST_URL)
                .header("authorization", "bearer " + TEST_ACCESS_TOKEN)
                .header("X-Forwarded-For", TEST_IP_ADDRESS)
                .header("X-App-Id", "badServiceName")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "url",
                            DRS_URI,
                            "cloudPlatform",
                            CloudPlatformEnum.GS,
                            "fields",
                            List.of()))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testHappyPathEmittingToBardWithResolvedCloudGCP() throws Exception {
    mockBardEmissionsEnabled();

    String accessUrl = "https://storage.googleapis.com/foo/bar";
    mockResolveDrsResponse(accessUrl);
    postRequest(
            REQUEST_URL,
            objectMapper.writeValueAsString(
                Map.of(
                    "url",
                    DRS_URI,
                    "cloudPlatform",
                    CloudPlatformEnum.GS,
                    "fields",
                    List.of("accessUrl"))))
        .andExpect(status().isOk());

    verify(trackingService)
        .logEvent(
            TEST_BEARER_TOKEN, EVENT_NAME, expectedBardProperties(List.of("accessUrl"), "gcp"));
  }

  @Test
  void testHappyPathEmittingToBardWithResolvedCloudAzure() throws Exception {
    mockBardEmissionsEnabled();

    String accessUrl = "https://foo.blob.windows.net/bar";
    mockResolveDrsResponse(accessUrl);
    postRequest(
            REQUEST_URL,
            objectMapper.writeValueAsString(
                Map.of(
                    "url",
                    DRS_URI,
                    "cloudPlatform",
                    CloudPlatformEnum.GS,
                    "fields",
                    List.of("accessUrl"))))
        .andExpect(status().isOk());

    verify(trackingService)
        .logEvent(
            TEST_BEARER_TOKEN, EVENT_NAME, expectedBardProperties(List.of("accessUrl"), "azure"));
  }

  @Test
  void testHappyPathEmittingToBardWithFileName() throws Exception {
    mockBardEmissionsEnabled();
    String fileName = "file-" + UUID.randomUUID();
    mockResolveDrsResponseWithFileName(fileName);
    postRequest(
            REQUEST_URL,
            objectMapper.writeValueAsString(
                Map.of(
                    "url",
                    DRS_URI,
                    "cloudPlatform",
                    CloudPlatformEnum.GS,
                    "fields",
                    List.of("fileName"))))
        .andExpect(status().isOk());

    HashMap<String, Object> expectedProperties = expectedBardProperties(List.of("fileName"), null);
    expectedProperties.put("fileName", fileName);
    verify(trackingService).logEvent(TEST_BEARER_TOKEN, EVENT_NAME, expectedProperties);
  }

  @Test
  void testEmittingToBardOn401Error() throws Exception {
    mockBardEmissionsEnabled();
    mockResolveDrsResponse(null);
    postRequest(
            REQUEST_URL,
            objectMapper.writeValueAsString(
                Map.of(
                    "url",
                    DRS_URI,
                    "cloudPlatform",
                    CloudPlatformEnum.GS,
                    "fields",
                    List.of("accessUrl"))))
        .andExpect(status().isOk());

    verify(trackingService)
        .logEvent(
            TEST_BEARER_TOKEN, EVENT_NAME, expectedBardProperties(List.of("accessUrl"), null));
  }

  @Test
  void testDoesNotLogWhenBardEmissionsDisabled() throws Exception {
    postRequest(
            REQUEST_URL,
            objectMapper.writeValueAsString(
                Map.of("url", DRS_URI, "cloudPlatform", CloudPlatformEnum.GS, "fields", List.of())))
        .andExpect(status().isOk());

    verifyNoInteractions(trackingService);
  }

  @Test
  void testDoesNotLogOn404() throws Exception {
    mockBardEmissionsEnabled();
    postRequest(
            REQUEST_URL,
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
            .header("X-App-Id", "terra_ui")
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

  private void mockResolveDrsResponse(String accessUrl) {
    AccessURL metadataAccessUrl = null;
    if (accessUrl != null) {
      metadataAccessUrl = new AccessURL().url(accessUrl);
    }
    DrsMetadata drsMetadata = new DrsMetadata.Builder().accessUrl(metadataAccessUrl).build();
    AnnotatedResourceMetadata metadata =
        AnnotatedResourceMetadata.builder()
            .requestedFields(List.of("accessUrl"))
            .drsMetadata(drsMetadata)
            .build();

    when(drsResolutionService.resolveDrsObject(
            anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(metadata));
  }

  private void mockResolveDrsResponseWithFileName(String fileName) {
    DrsMetadata drsMetadata = new DrsMetadata.Builder().fileName(fileName).build();
    AnnotatedResourceMetadata metadata =
        AnnotatedResourceMetadata.builder()
            .requestedFields(List.of("fileName"))
            .drsMetadata(drsMetadata)
            .build();
    when(drsResolutionService.resolveDrsObject(
            anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(metadata));
  }

  private HashMap<String, Object> expectedBardProperties(
      List<String> fields, String resolvedCloud) {
    HashMap<String, Object> properties =
        new HashMap<>(
            Map.of(
                "statusCode",
                200,
                "requestUrl",
                REQUEST_URL,
                "url",
                DRS_URI,
                "cloudPlatform",
                CloudPlatformEnum.GS.toString(),
                "fields",
                fields,
                "provider",
                "terraDataRepo",
                "serviceName",
                ServiceName.TERRA_UI.toString(),
                "transactionId",
                TRANSACTION_ID));
    if (resolvedCloud != null) {
      properties.put("resolvedCloud", resolvedCloud);
    }
    return properties;
  }

  private void mockProviders() {
    DrsProvider tdrProvider = DrsProvider.create();
    tdrProvider.setName("terraDataRepo");
    tdrProvider.setMetadataAuth(true);
    var accessMethod =
        ProviderAccessMethodConfig.create()
            .setType(AccessMethodConfigTypeEnum.gs)
            .setAuth(AccessUrlAuthEnum.fence_token)
            .setFetchAccessUrl(true);
    ArrayList<ProviderAccessMethodConfig> accessMethods = new ArrayList<>();
    accessMethods.add(accessMethod);
    tdrProvider.setAccessMethodConfigs(accessMethods);
    tdrProvider.setHostRegex(".*data.*[-.](?:broadinstitute\\.org|terra\\.bio)");
    when(drsHubConfig.getDrsProviders()).thenReturn(Map.of("terraDataRepo", tdrProvider));
  }
}
