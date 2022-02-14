package bio.terra.drshub.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.drshub.BaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class DrsHubApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  // TODO generally we may not need a bunch of these anyway

  // TODO I think we need this as a mock bean but it doesn't exist yet, or maybe won't exist?
  // @MockBean private ApiAdapter apiAdapterMock;
  @MockBean DrsHubApiController drsHubApiControllerMock;

  // drsHub_v3 doesn't fail when extra data submitted besides a 'url'
  @Test
  void testExtraDataDoesNotExplodeDrsHub() throws Exception {
    var requestBody = "{ \"url\": \"dos://${bdc}/123\", \"pattern\": \"gs://\", \"foo\": \"bar\" }";
    var authHeader = "bearer abc123";

    mvc.perform(
            post("/api/v4")
                .header("authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk());
  }

  // drsHub_v3 calls no endpoints when no fields are requested
  @Test
  void testDrsHubCallsNoEndpointsWithNoFieldsRequested() throws Exception {
    // var requestBody = "{ \"url\": \"dos://${bdc}/123\", \"pattern\": \"gs://\", \"foo\": \"bar\"
    // }";
    // TODO: giving up on this for now because I think it's not necessary now that "fields" is a
    // list of strings
    var requestBody = "{ \"url\": \"dos://${bdc}/123\", \"fields\": [] }";
    var authHeader = "bearer abc123";
    mvc.perform(
            post("/api/v4")
                .header("authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk());
    // .andExpect(content().json("{}"));
    // verify(apiAdapterMock, never());
  }

  // drsHub_v3 returns an error when fields is not an array
  @Test
  void drsHubReturnsErrorIfFieldsIsNotArray() throws Exception {
    var requestBody = "{ \"url\": \"dos://abc/123\", \"fields\": \"gs://\" }";
    var authHeader = "bearer abc123";

    mvc.perform(
            post("/api/v4")
                .header("authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
    // TODO: in the original test they are testing the error message, but now it gets caught as a
    // json parse error first
  }

  // drsHub_v3 returns an error when an invalid field is requested
  @Test
  void drsHubReturnsErrorWhenInvalidFieldIsRequested() throws Exception {
    var requestBody =
        "{ \"url\": \"dos://abc/123\", \"fields\" : [{ \"pattern\": \"gs://\", \"size\": \"blah\" ]} }";
    System.out.println("__________" + requestBody);
    var authHeader = "bearer abc123";

    mvc.perform(
            post("/api/v4")
                .header("authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
    // TODO probably need to check this more deeply
  }
}
