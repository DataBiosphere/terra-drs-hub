package bio.terra.drshub.services;

import bio.terra.datarepo.api.DataRepositoryServiceApi;
import bio.terra.datarepo.client.ApiClient;
import org.springframework.stereotype.Service;

@Service
public record TdrApiFactory() {

  public DataRepositoryServiceApi getApi(String accessToken, String baseUrl) {
    var apiClient = new ApiClient();
    apiClient.setBasePath(baseUrl);
    apiClient.setAccessToken(accessToken);
    return new DataRepositoryServiceApi(apiClient);
  }
}
