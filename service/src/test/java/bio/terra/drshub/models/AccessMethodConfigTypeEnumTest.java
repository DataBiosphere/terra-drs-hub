package bio.terra.drshub.models;

import static org.junit.jupiter.api.Assertions.*;

import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Unit")
class AccessMethodConfigTypeEnumTest {

  @Test
  void getReturnedEquivalent() {
    assertEquals(AccessMethodConfigTypeEnum.s3.getReturnedEquivalent(), TypeEnum.S3);
    assertEquals(AccessMethodConfigTypeEnum.gs.getReturnedEquivalent(), TypeEnum.GS);
    assertEquals(AccessMethodConfigTypeEnum.https.getReturnedEquivalent(), TypeEnum.HTTPS);
  }
}
