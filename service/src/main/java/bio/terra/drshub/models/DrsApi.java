package bio.terra.drshub.models;

import com.google.common.annotations.VisibleForTesting;
import io.github.ga4gh.drs.api.ObjectsApi;
import io.github.ga4gh.drs.client.ApiClient;
import io.github.ga4gh.drs.client.auth.OAuth;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.Authorizations;
import io.github.ga4gh.drs.model.DrsObject;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpServerErrorException.BadGateway;
import org.springframework.web.client.HttpServerErrorException.GatewayTimeout;
import org.springframework.web.client.HttpServerErrorException.InternalServerError;
import org.springframework.web.client.HttpServerErrorException.ServiceUnavailable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@Slf4j
public class DrsApi {
  static final int MAX_TRIALS = 4;

  private ObjectsApi objectsApi;

  public DrsApi(ApiClient apiClient) {
    this.objectsApi = new ObjectsApi(apiClient);
  }

  @VisibleForTesting
  public ApiClient getApiClient() {
    return objectsApi.getApiClient();
  }

  public void setBearerToken(String bearerToken) {
    ((OAuth) objectsApi.getApiClient().getAuthentication("BearerAuth")).setAccessToken(bearerToken);
  }

  public void setHeader(String name, String value) {
    objectsApi.getApiClient().addDefaultHeader(name, value);
  }

  public DrsObject postObject(Object body, String objectId) throws RestClientException {
    return retry(() -> objectsApi.postObject(body, objectId));
  }

  public AccessURL postAccessURL(Object body, String objectId, String accessId)
      throws RestClientException {
    return retry(() -> objectsApi.postAccessURL(body, objectId, accessId));
  }

  public Authorizations optionsObject(String objectId) throws RestClientException {
    return retry(() -> objectsApi.optionsObject(objectId));
  }

  public DrsObject getObject(String objectId, Boolean expand) throws RestClientException {
    return retry(() -> objectsApi.getObject(objectId, expand));
  }

  public AccessURL getAccessURL(String objectId, String accessId) throws RestClientException {
    return retry(() -> objectsApi.getAccessURL(objectId, accessId));
  }

  @VisibleForTesting
  static <T> T retry(Supplier<T> supplier) {
    for (int trial = 1; true; trial++) {
      try {
        return supplier.get();
      } catch (InternalServerError
          | BadGateway
          | ServiceUnavailable
          | GatewayTimeout
          | ResourceAccessException e) {
        if (trial == MAX_TRIALS) {
          throw e;
        } else {
          log.info(
              "retrying DRS call, attempt {} of {}, error {}", trial, MAX_TRIALS, e.getMessage());
          try {
            Thread.sleep(10L * trial);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw e;
          }
        }
      }
    }
  }
}
