package bio.terra.drshub.services;

import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.models.DrsApi;
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
  private final DrsApiClientFactory drsApiClientFactory;

  /**
   * We create a new ApiClient for each call so that headers and tokens are not shared between
   * calls, but each DRS provider can safely share one RestTemplate among its ApiClients.
   */
  private static final Map<String, RestTemplate> REST_TEMPLATE_CACHE =
      Collections.synchronizedMap(new HashMap<>());

  public DrsApiFactory(
      RestTemplateFactory restTemplateFactory, DrsApiClientFactory drsApiClientFactory) {
    this.restTemplateFactory = restTemplateFactory;
    this.drsApiClientFactory = drsApiClientFactory;
  }

  public DrsApi getApiFromUriComponents(UriComponents uriComponents, DrsProvider drsProvider) {
    log.debug(
        "Creating new DrsApi client for host '{}', for DRS Provider '{}'",
        uriComponents.getHost(),
        drsProvider.getName());
    var drsClient = drsApiClientFactory.createClient(getOrCreateRestTemplate(drsProvider));

    drsClient.setBasePath(
        drsClient
            .getBasePath()
            .replace("{serverURL}", Objects.requireNonNull(uriComponents.getHost())));

    return new DrsApi(drsClient);
  }

  private RestTemplate getOrCreateRestTemplate(DrsProvider drsProvider) {
    var name = drsProvider.getName();
    if (log.isDebugEnabled()) {
      // Normally checking for the presence of a key in a map is a lightweight operation, but we
      // don't want to obtain an unnecessary mutex lock on the backing synchronized map.
      if (REST_TEMPLATE_CACHE.containsKey(name)) {
        log.debug("Cache hit. Reusing RestTemplate for DRS Provider '{}'", name);
      }
    }

    return REST_TEMPLATE_CACHE.computeIfAbsent(
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
