package bio.terra.drshub.services;

import bio.terra.drshub.models.DrsApi;
import io.github.ga4gh.drs.client.ApiClient;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;

@Service
public class DrsApiFactory {

  private final RestTemplate mutualAuthRestTemplate;

  public DrsApiFactory(RestTemplate mutualAuthRestTemplate) {
    this.mutualAuthRestTemplate = mutualAuthRestTemplate;
  }

  public DrsApi getApiFromUriComponents(UriComponents uriComponents) {
    return getApiFromUriComponents(uriComponents, false);
  }

  public DrsApi getApiFromUriComponents(UriComponents uriComponents, boolean mTLS) {
    var drsClient = mTLS ? new ApiClient(mutualAuthRestTemplate) : new ApiClient();
    drsClient.setBasePath(
        drsClient
            .getBasePath()
            .replace("{serverURL}", Objects.requireNonNull(uriComponents.getHost())));

    return new DrsApi(drsClient);
  }
}
