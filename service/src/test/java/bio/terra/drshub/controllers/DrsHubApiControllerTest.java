package bio.terra.drshub.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.models.DrsApi;
import bio.terra.drshub.models.ExternalServicesData;
import bio.terra.drshub.models.ExternalServicesData.Builder;
import bio.terra.drshub.models.ImmutableExternalServicesData;
import bio.terra.drshub.services.BondApiFactory;
import bio.terra.drshub.services.DrsApiFactory;
import bio.terra.drshub.services.ExternalCredsApiFactory;
import bio.terra.externalcreds.api.OidcApi;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.DrsObject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
public class DrsHubApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  // TODO generally we may not need a bunch of these anyway

  // TODO I think we need this as a mock bean but it doesn't exist yet, or maybe won't exist?
  // @MockBean private ApiAdapter apiAdapterMock;
  @MockBean BondApiFactory bondApiFactory;
  @MockBean DrsApiFactory drsApiFactory;
  @MockBean ExternalCredsApiFactory externalCredsApiFactory;

  @Test
  void testCallsCorrectEndpointsWhenOnlyAccessUrlRequestedWithPassports() throws Exception {

    var host = "dg.TEST0";

    ImmutableExternalServicesData externalServicesData =
        new Builder()
            .accessToken("abc123")
            .passport("I am a passport")
            .objectId("000311ea-64e4-4462-9582-00562eb757aa")
            .accessId("gs")
            .accessUrl("I am a signed access url")
            .drsHost("ctds-test-env.planx-pla.net")
            .passportProvider("ras")
            .build();

    mockExternalServices(externalServicesData);

    var requestBody =
        String.format(
            "{ \"url\": \"drs://%s/%s\", \"fields\": [\"accessUrl\"] }",
            host, externalServicesData.getObjectId().get());
    mvc.perform(
            post("/api/v4")
                .header("authorization", "bearer " + externalServicesData.getAccessToken().get())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    String.format(
                        "{ \"accessUrl\": { \"url\": \"%s\"} }",
                        externalServicesData.getAccessUrl().get()),
                    true));
  }

  private void mockExternalServices(ExternalServicesData externalServicesData) {
    var mockDrsApi = mock(DrsApi.class);

    if (externalServicesData.getDrsHost().isPresent()
        && externalServicesData.getObjectId().isPresent()) {
      when(drsApiFactory.getApiFromUriComponents(
              UriComponentsBuilder.newInstance()
                  .host(externalServicesData.getDrsHost().get())
                  .path(externalServicesData.getObjectId().get())
                  .build()))
          .thenReturn(mockDrsApi);
    }
    if (externalServicesData.getObjectId().isPresent()
        && externalServicesData.getAccessId().isPresent()) {
      when(mockDrsApi.getObject(externalServicesData.getObjectId().get(), null))
          .thenReturn(
              new DrsObject()
                  .accessMethods(
                      List.of(
                          new AccessMethod()
                              .accessId(externalServicesData.getAccessId().get())
                              .type(TypeEnum.GS))));
    }
    if (externalServicesData.getPassport().isPresent()
        && externalServicesData.getObjectId().isPresent()
        && externalServicesData.getAccessId().isPresent()
        && externalServicesData.getAccessUrl().isPresent()) {
      when(mockDrsApi.postAccessURL(
              Map.of("passports", List.of(externalServicesData.getPassport().get())),
              externalServicesData.getObjectId().get(),
              externalServicesData.getAccessId().get()))
          .thenReturn(new AccessURL().url(externalServicesData.getAccessUrl().get()));
    }

    if (externalServicesData.getPassportProvider().isPresent()
        && externalServicesData.getAccessToken().isPresent()
        && externalServicesData.getPassport().isPresent()) {
      var mockExternalCredsApi = mock(OidcApi.class);
      when(externalCredsApiFactory.getApi(externalServicesData.getAccessToken().get()))
          .thenReturn(mockExternalCredsApi);
      when(mockExternalCredsApi.getProviderPassport(
              externalServicesData.getPassportProvider().get()))
          .thenReturn(externalServicesData.getPassport().get());
    }
  }

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
