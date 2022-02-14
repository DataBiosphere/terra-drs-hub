package bio.terra.drshub.services;

import bio.terra.drshub.models.DrsApi;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;

@Service
public class DrsApiFactory {

  public DrsApi getApiFromUriComponents(UriComponents uriComponents) {
    var drsApi = new DrsApi();
    var drsClient = drsApi.getApiClient();
    drsClient.setBasePath(
        drsClient
            .getBasePath()
            .replace("{serverURL}", Objects.requireNonNull(uriComponents.getHost())));

    return drsApi;
  }
}
