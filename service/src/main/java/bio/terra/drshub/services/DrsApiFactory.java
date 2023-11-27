package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.models.DrsApi;
import io.github.ga4gh.drs.client.ApiClient;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;

@Service
@Slf4j
public class DrsApiFactory {

  private final RestTemplateFactory restTemplateFactory;

  /**
   * We create a new ApiClient for each call so that headers and tokens are not shared between
   * calls, but each DRS provider can safely share one RestTemplate among its ApiClients.
   */
  private final Map<String, RestTemplate> restTemplateCache =
      Collections.synchronizedMap(new HashMap<>());

  public DrsApiFactory(RestTemplateFactory restTemplateFactory) {
    this.restTemplateFactory = restTemplateFactory;
  }

  public DrsApi getApiFromUriComponents(UriComponents uriComponents, DrsProvider drsProvider) {
    log.debug(
        "Creating new DrsApi client for host '{}', for DRS Provider '{}'",
        uriComponents.getHost(),
        drsProvider.getName());
    var drsClient = new ApiClient(getOrCreateRestTemplate(drsProvider));

    drsClient.setBasePath(
        drsClient
            .getBasePath()
            .replace("{serverURL}", Objects.requireNonNull(uriComponents.getHost())));

    return new DrsApi(drsClient);
  }

  private RestTemplate getOrCreateRestTemplate(DrsProvider drsProvider) {
    var name = drsProvider.getName();
    if (restTemplateCache.containsKey(name)) {
      log.debug("Cache hit. Reusing RestTemplate for DRS Provider '{}'", name);
    }
    return restTemplateCache.computeIfAbsent(
        name,
        n -> {
          log.info("Cache miss. Creating RestTemplate for DRS Provider '{}'", name);
          var mTlsConfig = drsProvider.getMTlsConfig();
          if (mTlsConfig == null) {
            return restTemplateFactory.makeRestTemplateWithPooling();
          }
          return restTemplateFactory.makeMTlsRestTemplateWithPooling(mTlsConfig);
        });
  }
}
