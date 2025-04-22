package bio.terra.drshub.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ga4gh.drs.client.ApiClient;
import io.github.ga4gh.drs.client.auth.OAuth;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

@Tag("Unit")
@ExtendWith(MockitoExtension.class)
class DrsApiTest {

  @Mock private ApiClient apiClient;

  private DrsApi drsApi;

  @BeforeEach
  void setup() {
    drsApi = new DrsApi(apiClient);
  }

  @Test
  void testSetBearerToken() {
    var accessToken = "bearerToken";
    var oAuth = mock(OAuth.class);
    when(apiClient.getAuthentication("BearerAuth")).thenReturn(oAuth);

    drsApi.setBearerToken(accessToken);

    verify(oAuth).setAccessToken(accessToken);
  }

  @Test
  void testSetHeader() {
    var name = "name";
    var value = "value";

    drsApi.setHeader(name, value);

    verify(apiClient).addDefaultHeader(name, value);
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        500, 502, 503, 504,
      }) // HttpStatus.INTERNAL_SERVER_ERROR, BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT
  void testRetries(int status) {
    AtomicInteger callCount = new AtomicInteger();
    assertThrows(
        HttpServerErrorException.class,
        () ->
            DrsApi.retry(
                () -> {
                  callCount.getAndIncrement();
                  throw HttpServerErrorException.create(
                      HttpStatus.valueOf(status), "error", null, null, null);
                }));
    assertEquals(DrsApi.MAX_TRIALS, callCount.get());
  }

  @Test
  void testDoesNotRetryNotFound() {
    AtomicInteger callCount = new AtomicInteger();
    assertThrows(
        HttpServerErrorException.class,
        () ->
            DrsApi.retry(
                () -> {
                  callCount.getAndIncrement();
                  throw HttpServerErrorException.create(
                      HttpStatus.NOT_FOUND, "Not Found", null, null, null);
                }));
    assertEquals(1, callCount.get());
  }

  @Test
  void testRetryReturnsValue() {
    AtomicInteger callCount = new AtomicInteger();
    var expected = "result";
    var actual =
        DrsApi.retry(
            () -> {
              callCount.getAndIncrement();
              return expected;
            });
    assertEquals(1, callCount.get());
    assertEquals(expected, actual);
  }
}
