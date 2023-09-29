package bio.terra.drshub.services;

import bio.terra.bard.api.BardApi;
import bio.terra.bard.client.ApiClient;
import bio.terra.bard.model.EventProperties;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.DrsHubException;
import bio.terra.drshub.config.DrsHubConfig;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.List;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public record BardApiFactory(DrsHubConfig drsHubConfig) {

  public BardApi getApi(BearerToken bearerToken) {
    var bardApi = new BardApi(new BardApiClient());

    bardApi.getApiClient().setBasePath(drsHubConfig.getBardUrl());
    bardApi.getApiClient().setAccessToken(bearerToken.getToken());

    return bardApi;
  }

  /**
   * Extension of the BardApiClient that overrides the buildRestTemplate method to add a custom
   * serializer for EventProperties
   */
  private static class BardApiClient extends ApiClient {
    @Override
    protected RestTemplate buildRestTemplate() {
      RestTemplate restTemplate = super.buildRestTemplate();

      MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
      converter.setObjectMapper(configureObjectMapper());
      restTemplate.setMessageConverters(List.of(converter));
      return restTemplate;
    }
  }

  @VisibleForTesting
  static ObjectMapper configureObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(EventProperties.class, new EventPropertiesSerializer());
    objectMapper.registerModule(module);
    return objectMapper;
  }

  /**
   * Custom serializer for EventProperties. This is needed because the Bard API expects the
   * arbitrary user properties to be serialized as top-level fields in the JSON object along with
   * the first class fields (e.g. appId and pushToMixpanel)
   */
  private static class EventPropertiesSerializer extends StdSerializer<EventProperties> {
    private static final List<String> RESERVED_FIELDS = List.of("appId", "pushToMixpanel");

    public EventPropertiesSerializer() {
      this(null);
    }

    public EventPropertiesSerializer(Class<EventProperties> t) {
      super(t);
    }

    @Override
    public void serialize(EventProperties value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException {
      jgen.writeStartObject();
      jgen.writeStringField("appId", value.getAppId());
      jgen.writeBooleanField("pushToMixpanel", value.isPushToMixpanel());
      value.forEach(
          (key, val) -> {
            try {
              // Ignore reserved fields since they are already written above
              if (!RESERVED_FIELDS.contains(key)) {
                jgen.writeObjectField(key, val);
              }
            } catch (IOException e) {
              throw new DrsHubException("Error serializing event properties", e);
            }
          });
      jgen.writeEndObject();
    }
  }
}
