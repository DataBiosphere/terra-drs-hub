package bio.terra.drshub.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.DrsHubException;
import bio.terra.drshub.generated.model.RequestObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class GlobalExceptionHandlerTest extends BaseTest {
  @MockBean DrsHubApiController drsHubApiControllerMock;
  @Autowired private MockMvc mvc;

  @Test
  void testBadRequest() throws Exception {
    when(drsHubApiControllerMock.getFile(new RequestObject()))
        .thenThrow(new IllegalArgumentException("bad"));
    mvc.perform(post("/api/v4")).andExpect(status().isBadRequest());
  }

  @Test
  void testInternalServerError() throws Exception {
    when(drsHubApiControllerMock.getFile(new RequestObject()))
        .thenThrow(new DrsHubException("sad"));
    mvc.perform(post("/api/v4")).andExpect(status().isInternalServerError());
  }
}
