package bio.terra.drshub.models;

import io.github.ga4gh.drs.api.ObjectsApi;
import io.github.ga4gh.drs.client.ApiClient;
import io.github.ga4gh.drs.client.auth.OAuth;

public class DrsApi extends ObjectsApi {

  public DrsApi(ApiClient apiClient) {
    super(apiClient);
  }

  public void setBearerToken(String bearerToken) {
    ((OAuth) this.getApiClient().getAuthentication("BearerAuth")).setAccessToken(bearerToken);
  }

  public void setHeader(String name, String value) {
    this.getApiClient().addDefaultHeader(name, value);
  }
}
