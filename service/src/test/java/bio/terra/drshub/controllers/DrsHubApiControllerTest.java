package bio.terra.drshub.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.bond.api.BondApi;
import bio.terra.bond.model.AccessTokenObject;
import bio.terra.bond.model.SaKeyObject;
import bio.terra.drshub.BaseTest;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
public class DrsHubApiControllerTest extends BaseTest {

  public static final String TEST_ACCESS_TOKEN = "I am an access token";
  public static final String TEST_BOND_SA_TOKEN = "I am a bond SA token";
  public static final AccessURL TEST_ACCESS_URL = new AccessURL().url("I am a signed access url");
  public static final String TEST_PASSPORT = "I am a passport";
  public static final String TDR_TEST_HOST = "jade.datarepo-dev.broadinstitute.org";
  @Autowired private DrsHubConfig config;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean BondApiFactory bondApiFactory;
  @MockBean DrsApiFactory drsApiFactory;
  @MockBean ExternalCredsApiFactory externalCredsApiFactory;

  @Test // 2
  void testCallsCorrectEndpointsWhenOnlyAccessUrlRequestedWithPassports() throws Exception {
    var cidProviderHost = getProviderHosts("passport");

    var drsObject = drsObjectWithRandomId("gs");

    mockDrsApiAccessUrlWithPassport(
        cidProviderHost.dnsHost, drsObject, TEST_PASSPORT, "gs", TEST_ACCESS_URL);

    mockExternalcredsApi("ras", TEST_ACCESS_TOKEN, Optional.of(TEST_PASSPORT));

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.drsUriHost,
            drsObject.getId(),
            List.of(Fields.ACCESS_URL))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    objectMapper.writeValueAsString(Map.of(Fields.ACCESS_URL, TEST_ACCESS_URL)),
                    true));
  }

  @Test // 3
  void testCallsCorrectEndpointsWhenOnlyAccessUrlRequestedWithPassportsUsingFallback()
      throws Exception {
    var cidProviderHost = getProviderHosts("passport");
    var drsObject = drsObjectWithRandomId("gs");

    var drsApi =
        mockDrsApiAccessUrlWithToken(cidProviderHost.dnsHost, drsObject, "gs", TEST_ACCESS_URL);

    mockExternalcredsApi("ras", TEST_ACCESS_TOKEN, Optional.empty());

    mockBondLinkAccessTokenApi(
        cidProviderHost.drsProvider.getBondProvider().get(), TEST_ACCESS_TOKEN, TEST_BOND_SA_TOKEN);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.drsUriHost,
            drsObject.getId(),
            List.of(Fields.ACCESS_URL))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    objectMapper.writeValueAsString(Map.of(Fields.ACCESS_URL, TEST_ACCESS_URL)),
                    true));

    // need an extra verify because nothing in the mock cares that bearer token is set or not
    verify(drsApi).setBearerToken(TEST_BOND_SA_TOKEN);
  }

  // 4 don't do, not supporting dos

  @Test // 5
  void testDoesNotFailWhenExtraDataSubmitted() throws Exception {
    var cidProviderHost = getProviderHosts("passport");
    var drsObject = drsObjectWithRandomId("gs");

    mockDrsApiAccessUrlWithToken(cidProviderHost.dnsHost, drsObject, "gs", TEST_ACCESS_URL);

    mockBondLinkAccessTokenApi(
        cidProviderHost.drsProvider.getBondProvider().get(), TEST_ACCESS_TOKEN, TEST_BOND_SA_TOKEN);

    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "url",
                String.format("drs://%s/%s", cidProviderHost.drsUriHost, drsObject.getId()),
                "fields",
                List.of(Fields.CONTENT_TYPE),
                "foo",
                "bar"));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isOk());
  }

  @Test // 6
  void testDoesNotCallBondWhenOnlyDrsFieldsRequested() throws Exception {
    var cidList = new ArrayList<>(config.getCompactIdHosts().keySet());
    var cid = cidList.get(new Random().nextInt(cidList.size()));
    var host = config.getCompactIdHosts().get(cid);
    var formatter = DateTimeFormatter.ISO_INSTANT;
    var updatedTime = new Date();

    var drsObject =
        new DrsObject()
            .id(UUID.randomUUID().toString())
            .size(new Random().nextLong())
            .checksums(List.of(new Checksum().type("md5").checksum("checksum")))
            .updatedTime(updatedTime)
            .name("filename")
            .accessMethods(
                List.of(
                    new AccessMethod()
                        .accessUrl(new AccessURL().url("gs://foobar"))
                        .type(TypeEnum.GS)));

    mockDrsApi(host, drsObject);

    List<String> requestedFields =
        List.of(Fields.GS_URI, Fields.SIZE, Fields.HASHES, Fields.TIME_UPDATED, Fields.FILE_NAME);

    Map<String, Object> drsObjectMap = drsObjectToMap(drsObject, requestedFields);
    drsObjectMap.put(Fields.TIME_UPDATED, formatter.format(updatedTime.toInstant()));
    postDrsHubRequest(TEST_ACCESS_TOKEN, cid, drsObject.getId(), requestedFields)
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(drsObjectMap), true));

    verify(bondApiFactory, times(0)).getApi(any());
  }

  @Test // 7
  void testDrsProviderDoesNotSupportGoogle() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.drsUriHost,
            UUID.randomUUID().toString(),
            List.of(Fields.GOOGLE_SERVICE_ACCOUNT))
        .andExpect(status().isOk())
        .andExpect(content().json("{}"));
  }

  @Test // 8
  void testDrsProviderDoesSupportGoogle() throws Exception {
    var cidProviderHost = getProviderHosts("bioDataCatalyst");

    var bondSaKey = Map.of("foo", "sa key");
    mockBondLinkSaKeyApi(
        cidProviderHost.drsProvider.getBondProvider().get(), TEST_ACCESS_TOKEN, bondSaKey);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.drsUriHost,
            UUID.randomUUID().toString(),
            List.of(Fields.GOOGLE_SERVICE_ACCOUNT))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    objectMapper.writeValueAsString(
                        Map.of(
                            Fields.GOOGLE_SERVICE_ACCOUNT,
                            content().json(new ObjectMapper().writeValueAsString(bondSaKey))))));
  }

  @Test // 8b
  void testCallsCorrectEndpointsWhenOnlyAccessUrlRequested() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var drsObject = drsObjectWithRandomId("s3");

    var drsApi =
        mockDrsApiAccessUrlWithToken(cidProviderHost.dnsHost, drsObject, "s3", TEST_ACCESS_URL);

    mockBondLinkAccessTokenApi(
        cidProviderHost.drsProvider.getBondProvider().get(), TEST_ACCESS_TOKEN, TEST_BOND_SA_TOKEN);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.drsUriHost,
            drsObject.getId(),
            List.of(Fields.ACCESS_URL))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    objectMapper.writeValueAsString(Map.of(Fields.ACCESS_URL, TEST_ACCESS_URL)),
                    true));

    // need an extra verify because nothing in the mock cares that bearer token is set or not
    verify(drsApi).setBearerToken(TEST_BOND_SA_TOKEN);
  }

  @Test // 9, 10, 11, 12
  void testForceFetchAccessUrlAllProviders() throws Exception {
    var providersList =
        config.getDrsProviders().entrySet().stream()
            .filter(
                p ->
                    p.getValue().getAccessMethodConfigs().stream()
                        .anyMatch(c -> !c.isFetchAccessUrl()))
            .map(Entry::getKey)
            .collect(Collectors.toList());

    for (var providerName : providersList) {
      var cidProviderHost = getProviderHosts(providerName);

      var accessMethod =
          cidProviderHost.drsProvider.getAccessMethodConfigs().stream()
              .filter(c -> !c.isFetchAccessUrl())
              .findAny()
              .get()
              .getType()
              .getReturnedEquivalent()
              .toString();

      var drsObject = drsObjectWithRandomId(accessMethod);

      mockDrsApiAccessUrlWithToken(
          cidProviderHost.dnsHost, drsObject, accessMethod, TEST_ACCESS_URL);

      cidProviderHost
          .drsProvider
          .getBondProvider()
          .ifPresent(p -> mockBondLinkAccessTokenApi(p, TEST_ACCESS_TOKEN, TEST_BOND_SA_TOKEN));

      mvc.perform(
              post("/api/v4")
                  .header("authorization", "bearer " + TEST_ACCESS_TOKEN)
                  .header("drshub-force-access-url", "true")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "url",
                              String.format(
                                  "drs://%s/%s", cidProviderHost.drsUriHost, drsObject.getId()),
                              "fields",
                              List.of(Fields.ACCESS_URL)))))
          .andExpect(status().isOk())
          .andExpect(
              content()
                  .json(
                      objectMapper.writeValueAsString(Map.of(Fields.ACCESS_URL, TEST_ACCESS_URL)),
                      true));
    }
  }

  @Test // 13, 15
  void testUsesProvidedFilename() throws Exception {
    var cidProviderHost = getProviderHosts("bioDataCatalyst");

    var fileName = "foo.bar.txt";
    var drsObject = drsObjectWithRandomId("gs").name(fileName);
    drsObject
        .getAccessMethods()
        .get(0)
        .setAccessUrl(new AccessURL().url("gs://bucket/bad.different.name.txt"));

    mockDrsApi(cidProviderHost.dnsHost, drsObject);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.drsUriHost,
            drsObject.getId(),
            List.of(Fields.FILE_NAME))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(objectMapper.writeValueAsString(Map.of(Fields.FILE_NAME, fileName)), true));
  }

  @Test // 14
  void testParsesMissingFilenameFromAccessUrl() throws Exception {
    var cidProviderHost = getProviderHosts("bioDataCatalyst");

    var drsObject = drsObjectWithRandomId("gs");
    var fileName = "foo.bar.txt";
    drsObject
        .getAccessMethods()
        .get(0)
        .setAccessUrl(new AccessURL().url("gs://bucket/" + fileName));

    mockDrsApi(cidProviderHost.dnsHost, drsObject);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.drsUriHost,
            drsObject.getId(),
            List.of(Fields.FILE_NAME))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(objectMapper.writeValueAsString(Map.of(Fields.FILE_NAME, fileName)), true));
  }

  @Test // 16
  void testReturns400WhenNoFieldsRequested() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", cidProviderHost.drsUriHost, "fields", List.of("")));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test // 17
  void testReturns400IfFieldsIsNotAList() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", cidProviderHost.drsUriHost, "fields", "not a list"));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test // 18
  void testReturns400IfInvalidFieldRequested() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "url",
                cidProviderHost.drsUriHost,
                "fields",
                List.of("fake field", "fake field 2")));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test // 19
  void testReturns401IfNoAuthHeaderIsSent() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", cidProviderHost.drsUriHost, "fields", List.of(Fields.CONTENT_TYPE)));

    mvc.perform(post("/api/v4").contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().is4xxClientError());
  }

  @Test // 20
  void testReturns400IfNotGivenUrl() throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("notAUrl", "drs://foo/bar", "fields", List.of(Fields.CONTENT_TYPE)));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test // 21
  void testReturns400IfGivenDgUrlWithoutPath() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", cidProviderHost.drsUriHost, "fields", List.of(Fields.CONTENT_TYPE)));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test // 22
  void testShouldReturn400IfGivenDgUrlWithOnlyPath() throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", UUID.randomUUID(), "fields", List.of(Fields.CONTENT_TYPE)));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test // 23
  void testShouldReturn400IfNoDataPostedWithRequest() throws Exception {
    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, "").andExpect(status().isBadRequest());
  }

  @Test // 24
  void testReturns400IfGivenInvalidUrlValue() throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", "I am not a url", "fields", List.of(Fields.CONTENT_TYPE)));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test // 25, 26
  void testShouldReturnUnderlyingStatusIfDataObjectResolutionFails() throws Exception {
    var cidList = new ArrayList<>(config.getCompactIdHosts().keySet());
    var cid = cidList.get(new Random().nextInt(cidList.size()));
    var host = config.getCompactIdHosts().get(cid);
    var drsObject = drsObjectWithRandomId("gs");

    when(mockDrsApi(host, drsObject).getObject(drsObject.getId(), null))
        .thenThrow(new HttpServerErrorException(HttpStatus.NOT_IMPLEMENTED, "forced sad response"));

    postDrsHubRequest(TEST_ACCESS_TOKEN, cid, drsObject.getId(), List.of(Fields.CONTENT_TYPE))
        .andExpect(status().is(HttpStatus.NOT_IMPLEMENTED.value()));
  }

  @Test // 27
  void testReturns500IfKeyRetrievalFromBondFails() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var drsObject = drsObjectWithRandomId("s3");

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.drsUriHost,
            drsObject.getId(),
            List.of(Fields.ACCESS_URL))
        .andExpect(status().is5xxServerError());
  }

  @Test // 45
  void testShouldReturnUnderlyingStatusIfGettingAccessUrlFails() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var drsObject = drsObjectWithRandomId("s3");

    when(mockDrsApi(cidProviderHost.dnsHost, drsObject).getAccessURL(drsObject.getId(), "s3"))
        .thenThrow(new HttpServerErrorException(HttpStatus.NOT_IMPLEMENTED, "forced sad response"));
    mockBondLinkAccessTokenApi(
        cidProviderHost.drsProvider.getBondProvider().get(), TEST_ACCESS_TOKEN, TEST_BOND_SA_TOKEN);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.drsUriHost,
            drsObject.getId(),
            List.of(Fields.ACCESS_URL))
        .andExpect(status().is(HttpStatus.NOT_IMPLEMENTED.value()));
  }

  @Test // 46
  void testReturnsNullForFieldsMissingInDrsResponse() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");

    var formatter = DateTimeFormatter.ISO_INSTANT;
    var createdTime = new Date(123);
    var drsObject = drsObjectWithRandomId("gs").createdTime(createdTime);
    List<String> requestedFields = List.of(Fields.TIME_CREATED, Fields.LOCALIZATION_PATH);

    var expectedMap = new HashMap<String, Object>();
    expectedMap.put(Fields.TIME_CREATED, formatter.format(createdTime.toInstant()));
    expectedMap.put(Fields.LOCALIZATION_PATH, null);

    mockDrsApi(cidProviderHost.dnsHost, drsObject);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN, cidProviderHost.drsUriHost, drsObject.getId(), requestedFields)
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(expectedMap), true));
  }

  private ProviderHosts getProviderHosts(String provider) {
    var drsProvider = config.getDrsProviders().get(provider);
    if (Objects.equals(provider, "terraDataRepo")) {
      return new ProviderHosts(TDR_TEST_HOST, TDR_TEST_HOST, drsProvider);
    }

    var drsHostRegex = Pattern.compile(drsProvider.getHostRegex());
    return config.getCompactIdHosts().entrySet().stream()
        .filter(h -> drsHostRegex.matcher(h.getValue()).matches())
        .findFirst()
        .map(entry -> new ProviderHosts(entry.getKey(), entry.getValue(), drsProvider))
        .get();
  }

  /**
   * Test utility function that extracts the right fields from a drs object into a Map that can be
   * added to and json-ified to compare to test results.
   *
   * @param drsObject
   * @param requestedFields
   * @return
   */
  private Map<String, Object> drsObjectToMap(DrsObject drsObject, List<String> requestedFields) {
    var responseMap = new HashMap<String, Object>();

    if (requestedFields.contains(Fields.FILE_NAME)) {
      responseMap.put(Fields.FILE_NAME, drsObject.getName());
    }
    if (requestedFields.contains(Fields.LOCALIZATION_PATH)) {
      responseMap.put(Fields.LOCALIZATION_PATH, drsObject.getAliases().get(0));
    }
    if (requestedFields.contains(Fields.TIME_CREATED)) {
      responseMap.put(Fields.TIME_CREATED, drsObject.getCreatedTime());
    }
    if (requestedFields.contains(Fields.TIME_UPDATED)) {
      responseMap.put(Fields.TIME_UPDATED, drsObject.getUpdatedTime());
    }
    if (requestedFields.contains(Fields.HASHES)) {
      responseMap.put(
          Fields.HASHES,
          drsObject.getChecksums().stream()
              .collect(Collectors.toMap(Checksum::getType, Checksum::getChecksum)));
    }
    if (requestedFields.contains(Fields.SIZE)) {
      responseMap.put(Fields.SIZE, drsObject.getSize());
    }
    if (requestedFields.contains(Fields.CONTENT_TYPE)) {
      responseMap.put(Fields.CONTENT_TYPE, drsObject.getMimeType());
    }

    if (requestedFields.contains(Fields.GS_URI)) {
      responseMap.put(
          Fields.GS_URI,
          drsObject.getAccessMethods().stream()
              .filter(m -> m.getType() == TypeEnum.GS)
              .findFirst()
              .get()
              .getAccessUrl()
              .getUrl());
    }

    return responseMap;
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

  private BondApi mockBondLinkAccessTokenApi(
      BondProviderEnum bondProvider, String accessToken, String bondSaToken) {
    var mockBondApi = mock(BondApi.class);
    when(bondApiFactory.getApi(accessToken)).thenReturn(mockBondApi);
    when(mockBondApi.getLinkAccessToken(bondProvider.name()))
        .thenReturn(new AccessTokenObject().token(bondSaToken));
    return mockBondApi;
  }

  private BondApi mockBondLinkSaKeyApi(
      BondProviderEnum bondProvider, String accessToken, Object bondSaKey) {
    var mockBondApi = mock(BondApi.class);
    when(bondApiFactory.getApi(accessToken)).thenReturn(mockBondApi);
    when(mockBondApi.getLinkSaKey(bondProvider.name()))
        .thenReturn(new SaKeyObject().data(bondSaKey));
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

  private static class ProviderHosts {
    final String drsUriHost;
    final String dnsHost;
    final DrsProvider drsProvider;

    public ProviderHosts(String drsUriHost, String dnsHost, DrsProvider drsProvider) {
      this.drsUriHost = drsUriHost;
      this.dnsHost = dnsHost;
      this.drsProvider = drsProvider;
    }
  }

  private DrsObject drsObjectWithRandomId(String accessMethod) {
    return new DrsObject()
        .id(UUID.randomUUID().toString())
        .accessMethods(
            List.of(
                new AccessMethod().accessId(accessMethod).type(TypeEnum.fromValue(accessMethod))));
  }
}
