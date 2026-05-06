package bio.terra.drshub.services;

import bio.terra.datarepo.api.DataRepositoryServiceApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.drshub.config.DrsHubConfig;
import org.springframework.stereotype.Service;

@Service
public record TdrApiFactory(DrsHubConfig drsHubConfig) {

  public DataRepositoryServiceApi getApi(String accessToken) {
    var apiClient = new ApiClient();
    apiClient.setBasePath(drsHubConfig.getTdrUrl());
    apiClient.setAccessToken(accessToken);
    return new DataRepositoryServiceApi(apiClient);
  }
}
