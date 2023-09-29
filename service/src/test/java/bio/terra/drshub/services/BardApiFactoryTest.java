package bio.terra.drshub.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.bard.model.Event;
import bio.terra.bard.model.EventProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Unit")
class BardApiFactoryTest {

  @Test
  void testObjectMapper() throws JsonProcessingException {
    ObjectMapper objectMapper = BardApiFactory.configureObjectMapper();

    EventProperties eventProperties = new EventProperties().appId("appId").pushToMixpanel(false);
    eventProperties.put("foo", "bar");
    eventProperties.put("appId", "otherAppId");
    Event event = new Event().event("event").properties(eventProperties);
    assertThat(
        "Event serializes properly",
        objectMapper.writeValueAsString(event),
        equalTo(
            """
            {"event":"event","properties":{"appId":"appId","pushToMixpanel":false,"foo":"bar"}}"""));
  }
}
