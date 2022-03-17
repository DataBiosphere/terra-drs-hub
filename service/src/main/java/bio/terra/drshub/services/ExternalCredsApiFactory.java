package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.externalcreds.api.OidcApi;
import bio.terra.externalcreds.client.ApiClient;
import java.util.List;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ExternalCredsApiFactory {

  private final DrsHubConfig drsHubConfig;
  private final MappingJackson2HttpMessageConverter jacksonConverter;

  public ExternalCredsApiFactory(
      DrsHubConfig drsHubConfig, MappingJackson2HttpMessageConverter jacksonConverter) {
    this.drsHubConfig = drsHubConfig;
    this.jacksonConverter = jacksonConverter;
  }

  public OidcApi getApi(String accessToken) {
    // ECM returns lots of responses as JSON string.
    // By default, these get intercepted by Spring's raw string converter, but they're quoted, so
    // they should be parsed as JSON.
    // N.B. This means that any non-JSON responses won't be parsed correctly, so if ECM ever returns
    // any other content-type, this would have to be revised. However, that seems unlikely.
    var restTemplate = new RestTemplate(List.of(jacksonConverter));

    var client = new ApiClient(restTemplate);
    var ecmApi = new OidcApi(client);
    ecmApi.getApiClient().setBasePath(drsHubConfig.getExternalcredsUrl());
    ecmApi.getApiClient().setAccessToken(accessToken);

    return ecmApi;
  }
}
