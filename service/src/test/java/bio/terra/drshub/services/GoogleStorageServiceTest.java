package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.generated.model.SaKeyObject;
import bio.terra.drshub.util.SignedUrlTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("Unit")
public class GoogleStorageServiceTest extends BaseTest {

  @Autowired GoogleStorageService googleStorageService;
  @Autowired ObjectMapper objectMapper;

  @Test
  public void testGetAuthedStorage() throws Exception {
    var googleProject = "my-google-project";
    String saKeyString = SignedUrlTestUtils.generateSaKeyObjectString();
    var saKeyObject = objectMapper.readValue(saKeyString, SaKeyObject.class);
    var storage = googleStorageService.getAuthedStorage(saKeyObject, "my-google-project");

    assertEquals(googleProject, storage.getOptions().getProjectId());
  }
}
