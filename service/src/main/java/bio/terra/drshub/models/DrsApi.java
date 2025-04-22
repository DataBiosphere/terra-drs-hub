package bio.terra.drshub.models;

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
public class DrsApi extends ObjectsApi {
  static final int MAX_TRIALS = 4;

  public DrsApi(ApiClient apiClient) {
    super(apiClient);
  }

  public void setBearerToken(String bearerToken) {
    ((OAuth) this.getApiClient().getAuthentication("BearerAuth")).setAccessToken(bearerToken);
  }

  public void setHeader(String name, String value) {
    this.getApiClient().addDefaultHeader(name, value);
  }

  @Override
  public DrsObject postObject(Object body, String objectId) throws RestClientException {
    return retry(() -> super.postObject(body, objectId));
  }

  @Override
  public AccessURL postAccessURL(Object body, String objectId, String accessId)
      throws RestClientException {
    return retry(() -> super.postAccessURL(body, objectId, accessId));
  }

  @Override
  public Authorizations optionsObject(String objectId) throws RestClientException {
    return retry(() -> super.optionsObject(objectId));
  }

  @Override
  public DrsObject getObject(String objectId, Boolean expand) throws RestClientException {
    return retry(() -> super.getObject(objectId, expand));
  }

  @Override
  public AccessURL getAccessURL(String objectId, String accessId) throws RestClientException {
    return retry(() -> super.getAccessURL(objectId, accessId));
  }

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
            throw e;
          }
        }
      }
    }
  }
}
