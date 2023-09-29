package bio.terra.drshub.services;

import static bio.terra.drshub.services.TrackingService.APP_ID;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.bard.api.BardApi;
import bio.terra.bard.model.Event;
import bio.terra.bard.model.EventProperties;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.BaseTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;

@Tag("Unit")
class TrackingServiceTest extends BaseTest {
  private static final String TEST_ACCESS_TOKEN = "I_am_an_access_token";
  private static final BearerToken TEST_BEARER_TOKEN = new BearerToken(TEST_ACCESS_TOKEN);

  @MockBean BardApiFactory bardApiFactory;
  BardApi bardApi;
  @Autowired TrackingService trackingService;

  @BeforeEach
  void setUp() {
    trackingService.clearCache();
    bardApi = mock(BardApi.class);
    when(bardApiFactory.getApi(TEST_BEARER_TOKEN)).thenReturn(bardApi);
  }

  @Test
  void testLogEventHappyPath() {
    when(bardApi.syncProfileWithHttpInfo()).thenReturn(ResponseEntity.ok(null));
    when(bardApi.eventWithHttpInfo(any())).thenReturn(ResponseEntity.ok(null));
    trackingService.logEvent(TEST_BEARER_TOKEN, "foo", Map.of("bar", "baz"));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> verify(bardApi).syncProfileWithHttpInfo());

    var expectedEventProperties = new EventProperties().appId(APP_ID).pushToMixpanel(false);
    expectedEventProperties.put("bar", "baz");
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                verify(bardApi, times(1))
                    .eventWithHttpInfo(
                        new Event().event("foo").properties(expectedEventProperties)));
  }

  @Test
  void testLogEventWhenBardIsDown() {
    when(bardApi.syncProfileWithHttpInfo())
        .thenReturn(ResponseEntity.internalServerError().build());
    when(bardApi.eventWithHttpInfo(any())).thenReturn(ResponseEntity.internalServerError().build());
    trackingService.logEvent(TEST_BEARER_TOKEN, "foo", Map.of("bar", "baz"));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> verify(bardApi).syncProfileWithHttpInfo());

    var expectedEventProperties = new EventProperties().appId(APP_ID).pushToMixpanel(false);
    expectedEventProperties.put("bar", "baz");
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                verify(bardApi, times(1))
                    .eventWithHttpInfo(
                        new Event().event("foo").properties(expectedEventProperties)));
  }
}
