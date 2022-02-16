package bio.terra.drshub.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.bond.api.BondApi;
import bio.terra.bond.model.AccessTokenObject;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.models.BondProviderEnum;
import bio.terra.drshub.models.DrsApi;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.BondApiFactory;
import bio.terra.drshub.services.DrsApiFactory;
import bio.terra.drshub.services.ExternalCredsApiFactory;
import bio.terra.externalcreds.api.OidcApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.Checksum;
import io.github.ga4gh.drs.model.DrsObject;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
public class DrsHubApiControllerTest extends BaseTest {

  public static final String TEST_ACCESS_TOKEN = "I am an access token";
  public static final String TEST_BOND_SA_TOKEN = "I am a bond SA token";
  public static final AccessURL TEST_ACCESS_URL = new AccessURL().url("I am a signed access url");
  public static final String TEST_PASSPORT = "I am a passport";
  @Autowired private DrsHubConfig config;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean BondApiFactory bondApiFactory;
  @MockBean DrsApiFactory drsApiFactory;
  @MockBean ExternalCredsApiFactory externalCredsApiFactory;

  @Test // 2
  void testCallsCorrectEndpointsWhenOnlyAccessUrlRequestedWithPassports() throws Exception {
    var passportDrsProvider =
        config.getDrsProviders().stream()
            .filter(p -> p.getId().equals("passport"))
            .findFirst()
            .get();
    var host = passportDrsProvider.getDgCompactIds().get(0);
    var drsObject =
        new DrsObject()
            .id(UUID.randomUUID().toString())
            .accessMethods(List.of(new AccessMethod().accessId("gs").type(TypeEnum.GS)));

    mockDrsApiAccessUrlWithPassport(
        config.getHosts().get(passportDrsProvider.getId()),
        drsObject,
        TEST_PASSPORT,
        "gs",
        TEST_ACCESS_URL);

    mockExternalcredsApi("ras", TEST_ACCESS_TOKEN, Optional.of(TEST_PASSPORT));

    postDrsHubRequest(TEST_ACCESS_TOKEN, host, drsObject.getId(), List.of(Fields.ACCESS_URL))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    objectMapper.writeValueAsString(
                        Map.of(Fields.ACCESS_URL, Map.of("url", TEST_ACCESS_URL.getUrl()))),
                    true));
  }

  @Test // 3
  void testCallsCorrectEndpointsWhenOnlyAccessUrlRequestedWithPassportsUsingFallback()
      throws Exception {
    var passportDrsProvider =
        config.getDrsProviders().stream()
            .filter(p -> p.getId().equals("passport"))
            .findFirst()
            .get();
    var host = passportDrsProvider.getDgCompactIds().get(0);
    var drsObject =
        new DrsObject()
            .id(UUID.randomUUID().toString())
            .accessMethods(List.of(new AccessMethod().accessId("gs").type(TypeEnum.GS)));

    var drsApi =
        mockDrsApiAccessUrlWithToken(
            config.getHosts().get(passportDrsProvider.getId()), drsObject, "gs", TEST_ACCESS_URL);

    mockExternalcredsApi("ras", TEST_ACCESS_TOKEN, Optional.empty());

    mockBondApi(BondProviderEnum.dcf_fence, TEST_ACCESS_TOKEN, TEST_BOND_SA_TOKEN);

    postDrsHubRequest(TEST_ACCESS_TOKEN, host, drsObject.getId(), List.of(Fields.ACCESS_URL))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    objectMapper.writeValueAsString(
                        Map.of(Fields.ACCESS_URL, Map.of("url", TEST_ACCESS_URL.getUrl()))),
                    true));

    // need an extra verify because nothing in the mock cares that bearer token is set or not
    verify(drsApi).setBearerToken(TEST_BOND_SA_TOKEN);
  }

  // 4 don't do, not supporting dos

  @Test // 5
  void testDoesNotFailWhenExtraDataSubmitted() throws Exception {
    var drsProvider =
        config.getDrsProviders().stream()
            .filter(p -> !p.getDgCompactIds().isEmpty() && p.getBondProvider().isPresent())
            .findAny()
            .get();
    var host = drsProvider.getDgCompactIds().get(0);
    var drsObject =
        new DrsObject()
            .id(UUID.randomUUID().toString())
            .accessMethods(List.of(new AccessMethod().accessId("gs").type(TypeEnum.GS)));

    mockDrsApiAccessUrlWithToken(
        config.getHosts().get(drsProvider.getId()), drsObject, "gs", TEST_ACCESS_URL);

    mockBondApi(drsProvider.getBondProvider().get(), TEST_ACCESS_TOKEN, TEST_BOND_SA_TOKEN);

    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "url",
                String.format("drs://%s/%s", host, drsObject.getId()),
                "fields",
                List.of(Fields.CONTENT_TYPE),
                "foo",
                "bar"));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isOk());
  }

  @Test // 6
  void testDoesNotCallBondWhenOnlyDrsFieldsRequested() throws Exception {
    var drsProvider =
        config.getDrsProviders().stream()
            .filter(p -> !p.getDgCompactIds().isEmpty() && p.getBondProvider().isPresent())
            .findAny()
            .get();
    var host = drsProvider.getDgCompactIds().get(0);
    var drsObject =
        new DrsObject()
            .id(UUID.randomUUID().toString())
            .size(new Random().nextLong())
            .checksums(List.of(new Checksum().type("md5").checksum("checksum")))
            .updatedTime(new Date())
            .name("filename")
            .accessMethods(
                List.of(
                    new AccessMethod()
                        .accessUrl(new AccessURL().url("gs://foobar"))
                        .type(TypeEnum.GS)));

    mockDrsApi(config.getHosts().get(drsProvider.getId()), drsObject);
    when(bondApiFactory.getApi(any()))
        .thenThrow(new RuntimeException("If I am thrown this is a failure"));

    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "url",
                String.format("drs://%s/%s", host, drsObject.getId()),
                "fields",
                List.of(
                    Fields.GS_URI,
                    Fields.SIZE,
                    Fields.HASHES,
                    Fields.TIME_UPDATED,
                    Fields.FILE_NAME)));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody)
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "size",
                            drsObject.getSize(),
                            "timeUpdated",
                            drsObject.getUpdatedTime(),
                            "gsUri",
                            "gs://foobar",
                            "fileName",
                            drsObject.getName(),
                            "hashes",
                            Map.of("md5", "checksum")))));
  }

  private ResultActions postDrsHubRequest(
      String accessToken, String host, String objectId, List<String> fields) throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", String.format("drs://%s/%s", host, objectId), "fields", fields));

    return postDrsHubRequestRaw(accessToken, requestBody);
  }

  private ResultActions postDrsHubRequestRaw(String accessToken, String requestBody)
      throws Exception {
    return mvc.perform(
        post("/api/v4")
            .header("authorization", "bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
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
}
