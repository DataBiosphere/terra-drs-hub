package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.externalcreds.api.OidcApi;
import org.springframework.stereotype.Service;

@Service
public class ExternalCredsApiFactory {

  private final DrsHubConfig drsHubConfig;

  public ExternalCredsApiFactory(DrsHubConfig drsHubConfig) {
    this.drsHubConfig = drsHubConfig;
  }

  public OidcApi getApi(String accessToken) {
    var ecmApi = new OidcApi();
    ecmApi.getApiClient().setBasePath(drsHubConfig.getExternalcredsUrl());
    ecmApi.getApiClient().setAccessToken(accessToken);

    return ecmApi;
  }
}
