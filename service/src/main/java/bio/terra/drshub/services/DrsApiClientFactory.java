package bio.terra.drshub.services;

import io.github.ga4gh.drs.client.ApiClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DrsApiClientFactory {

  ApiClient createClient(RestTemplate restTemplate) {
    return new ApiClient(restTemplate);
  }
}
