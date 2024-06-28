package bio.terra.drshub.models;

import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.drshub.BaseTest;
import io.github.ga4gh.drs.model.DrsObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.boot.test.json.JacksonTester;

@Tag("Unit")
@AutoConfigureJson
@AutoConfigureJsonTesters
class AnnotatedResourceMetadataSerializerTest extends BaseTest {

  @Autowired private JacksonTester<AnnotatedResourceMetadata> jacksonTester;

  @Test
  void testHandlesNullDates() throws IOException {
    var drsResponse = new DrsObject().accessMethods(List.of()); // 2020-04-27T15:56:09.696Z
    AnnotatedResourceMetadata metadata =
        new AnnotatedResourceMetadata(
            List.of(Fields.TIME_CREATED, Fields.TIME_UPDATED),
            new DrsMetadata.Builder().drsResponse(drsResponse).build(),
            config.getDrsProviders().get("terraDataRepo"),
            "fake.ip");

    var written = jacksonTester.write(metadata);
    assertTrue(written.getJson().contains("\"timeCreated\" : null"));
    assertTrue(written.getJson().contains("\"timeUpdated\" : null"));
  }
}
