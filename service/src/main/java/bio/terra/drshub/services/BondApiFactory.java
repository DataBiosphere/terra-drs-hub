package bio.terra.drshub.services;

import bio.terra.bond.api.BondApi;
import bio.terra.common.iam.TokenAuthenticatedRequest;
import bio.terra.drshub.config.DrsHubConfig;
import org.springframework.stereotype.Service;

@Service
public record BondApiFactory(DrsHubConfig drsHubConfig) {

  public BondApi getApi(TokenAuthenticatedRequest tokenAuthenticatedRequest) {
    var bondApi = new BondApi();
    bondApi.getApiClient().setBasePath(drsHubConfig.getBondUrl());
    bondApi.getApiClient().setAccessToken(tokenAuthenticatedRequest.getToken());

    return bondApi;
  }
}
