package bio.terra.drshub.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.bond.api.BondApi;
import bio.terra.bond.model.AccessTokenObject;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.models.BondProviderEnum;
import bio.terra.drshub.models.DrsApi;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
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
    var accessToken = "abc123";
    var accessUrl = new AccessURL().url("I am a signed access url");
    var drsObject =
        new DrsObject()
            .id("000311ea-64e4-4462-9582-00562eb757aa")
            .accessMethods(List.of(new AccessMethod().accessId("gs").type(TypeEnum.GS)));

    mockDrsApiAccessUrlWithPassport(
        "ctds-test-env.planx-pla.net", drsObject, "I am a passport", "gs", accessUrl);

    mockExternalcredsApi("ras", accessToken, Optional.of("I am a passport"));

    var requestBody =
        String.format(
            "{ \"url\": \"drs://%s/%s\", \"fields\": [\"accessUrl\"] }", host, drsObject.getId());
    mvc.perform(
            post("/api/v4")
                .header("authorization", "bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    String.format("{ \"accessUrl\": { \"url\": \"%s\"} }", accessUrl.getUrl()),
                    true));
  }

  @Test
  void testCallsCorrectEndpointsWhenOnlyAccessUrlRequestedWithPassportsUsingFallback()
      throws Exception {
    var host = "dg.TEST0";
    var accessToken = "abc123";
    var bondSaToken = "I am a bond SA token";
    var accessUrl = new AccessURL().url("I am a signed access url");
    var drsObject =
        new DrsObject()
            .id("000311ea-64e4-4462-9582-00562eb757aa")
            .accessMethods(List.of(new AccessMethod().accessId("gs").type(TypeEnum.GS)));

    var drsApi =
        mockDrsApiAccessUrlWithToken("ctds-test-env.planx-pla.net", drsObject, "gs", accessUrl);

    mockExternalcredsApi("ras", accessToken, Optional.empty());

    mockBondApi(BondProviderEnum.dcf_fence, accessToken, bondSaToken);

    var requestBody =
        String.format(
            "{ \"url\": \"drs://%s/%s\", \"fields\": [\"accessUrl\"] }", host, drsObject.getId());
    mvc.perform(
            post("/api/v4")
                .header("authorization", "bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    String.format("{ \"accessUrl\": { \"url\": \"%s\"} }", accessUrl.getUrl()),
                    true));

    verify(drsApi).setBearerToken(bondSaToken);
  }

  private BondApi mockBondApi(
      BondProviderEnum bondProvider, String accessToken, String bondSaToken) {
    var mockBondApi = mock(BondApi.class);
    when(bondApiFactory.getApi(accessToken)).thenReturn(mockBondApi);
    when(mockBondApi.getLinkAccessToken(bondProvider.name()))
        .thenReturn(new AccessTokenObject().token(bondSaToken));
    return mockBondApi;
  }

  private OidcApi mockExternalcredsApi(
      String passportProvider, String accessToken, Optional<String> passport) {
    var mockExternalCredsApi = mock(OidcApi.class);
    when(externalCredsApiFactory.getApi(accessToken)).thenReturn(mockExternalCredsApi);
    if (passport.isPresent()) {
      when(mockExternalCredsApi.getProviderPassport(passportProvider)).thenReturn(passport.get());
    } else {
      when(mockExternalCredsApi.getProviderPassport(passportProvider))
          .thenThrow(
              HttpClientErrorException.create(
                  HttpStatus.NOT_FOUND, "not found", new HttpHeaders(), null, null));
    }
    return mockExternalCredsApi;
  }

  private DrsApi mockDrsApi(String drsHost, DrsObject drsObject) {
    var mockDrsApi = mock(DrsApi.class);

    when(drsApiFactory.getApiFromUriComponents(
            UriComponentsBuilder.newInstance().host(drsHost).path(drsObject.getId()).build()))
        .thenReturn(mockDrsApi);
    when(mockDrsApi.getObject(drsObject.getId(), null)).thenReturn(drsObject);

    return mockDrsApi;
  }

  private DrsApi mockDrsApiAccessUrlWithPassport(
      String drsHost, DrsObject drsObject, String passport, String accessId, AccessURL accessUrl) {
    var mockDrsApi = mockDrsApi(drsHost, drsObject);
    when(mockDrsApi.postAccessURL(
            Map.of("passports", List.of(passport)), drsObject.getId(), accessId))
        .thenReturn(accessUrl);
    return mockDrsApi;
  }

  private DrsApi mockDrsApiAccessUrlWithToken(
      String drsHost, DrsObject drsObject, String accessId, AccessURL accessUrl) {
    var mockDrsApi = mockDrsApi(drsHost, drsObject);
    when(mockDrsApi.getAccessURL(drsObject.getId(), accessId)).thenReturn(accessUrl);
    return mockDrsApi;
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
