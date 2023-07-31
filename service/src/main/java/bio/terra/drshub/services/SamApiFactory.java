package bio.terra.drshub.services;

import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.sam.api.SamApi;
import org.springframework.stereotype.Service;

@Service
public record SamApiFactory(DrsHubConfig drsHubConfig) {

  public SamApi getApi(BearerToken bearerToken) {
    var samApi = new SamApi();
    samApi.getApiClient().setBasePath(drsHubConfig.getSamUrl());
    samApi.getApiClient().setAccessToken(bearerToken.getToken());

    return samApi;
  }
}
