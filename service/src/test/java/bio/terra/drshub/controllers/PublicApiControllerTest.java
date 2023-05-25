package bio.terra.drshub.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.VersionProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@Tag("Unit")
@AutoConfigureMockMvc
public class PublicApiControllerTest extends BaseTest {
  @Autowired private MockMvc mvc;

  @MockBean DrsHubConfig configMock;

  @Test
  void testGetStatus() throws Exception {
    mvc.perform(get("/status")).andExpect(status().isOk());
  }

  @Test
  void testGetVersion() throws Exception {
    var versionProperties =
        VersionProperties.create()
            .setGitTag("gitTag")
            .setGitHash("gitHash")
            .setBuild("build")
            .setGithub("github");
    when(configMock.getVersion()).thenReturn(versionProperties);

    mvc.perform(get("/version"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"gitTag": "gitTag", "gitHash": "gitHash", "github": "github", "build": "build"}"""));
  }
}
