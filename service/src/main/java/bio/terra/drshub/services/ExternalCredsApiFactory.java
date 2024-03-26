package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.externalcreds.api.FenceAccountKeyApi;
import bio.terra.externalcreds.api.OauthApi;
import bio.terra.externalcreds.api.OidcApi;
import bio.terra.externalcreds.client.ApiClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public record ExternalCredsApiFactory(DrsHubConfig drsHubConfig) {

  public ApiClient getApi(String accessToken) {
    var restTemplate = new RestTemplate();
    var client = new ApiClient(restTemplate);
    client.setBasePath(drsHubConfig.getExternalcredsUrl());
    client.setAccessToken(accessToken);

    return client;
  }

  public OauthApi getOauthApi(String accessToken) {
    var client = getApi(accessToken);
    return new OauthApi(client);
  }

  public OidcApi getOidcApi(String accessToken) {
    var client = getApi(accessToken);
    return new OidcApi(client);
  }

  public FenceAccountKeyApi getFenceAccountKeyApi(String accessToken) {
    var client = getApi(accessToken);
    return new FenceAccountKeyApi(client);
  }
}
