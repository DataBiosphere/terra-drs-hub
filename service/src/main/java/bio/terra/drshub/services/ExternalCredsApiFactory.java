package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.externalcreds.api.OauthApi;
import bio.terra.externalcreds.api.OidcApi;
import bio.terra.externalcreds.client.ApiClient;
import java.util.List;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public record ExternalCredsApiFactory(
    DrsHubConfig drsHubConfig, MappingJackson2HttpMessageConverter jacksonConverter) {

  public ApiClient getApi(String accessToken) {
    // ECM returns lots of responses as JSON string.
    // By default, these get intercepted by Spring's raw string converter, but they're quoted, so
    // they should be parsed as JSON.
    // N.B. This means that any non-JSON responses won't be parsed correctly, so if ECM ever returns
    // any other content-type, this would have to be revised. However, that seems unlikely.
    var restTemplate = new RestTemplate(List.of(jacksonConverter));

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
}
