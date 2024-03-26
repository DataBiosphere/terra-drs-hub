package bio.terra.drshub.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.generated.model.SaKeyObject;
import bio.terra.drshub.models.DrsApi;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.AuthService;
import bio.terra.drshub.services.DrsApiFactory;
import bio.terra.drshub.services.ExternalCredsApiFactory;
import bio.terra.externalcreds.api.FenceAccountKeyApi;
import bio.terra.externalcreds.api.OauthApi;
import bio.terra.externalcreds.api.OidcApi;
import bio.terra.externalcreds.model.PassportProvider;
import bio.terra.externalcreds.model.Provider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ga4gh.drs.client.ApiClient;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessMethod.TypeEnum;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.AllOfAccessMethodAccessUrl;
import io.github.ga4gh.drs.model.Authorizations;
import io.github.ga4gh.drs.model.Authorizations.SupportedTypesEnum;
import io.github.ga4gh.drs.model.Checksum;
import io.github.ga4gh.drs.model.DrsObject;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Tag("Unit")
@AutoConfigureMockMvc
public class DrsHubApiControllerTest extends BaseTest {

  public static final String TEST_ACCESS_TOKEN = "I_am_an_access_token";
  public static final String TEST_FENCE_SA_TOKEN = "I am a fence SA token";
  public static final AccessURL TEST_ACCESS_URL = new AccessURL().url("I am a signed access url");
  public static final String TEST_PASSPORT = "I am a passport";
  public static final String COMPACT_ID_TEST_HOST = "drs.anv0";
  public static final String TDR_TEST_HOST = "jade.datarepo-dev.broadinstitute.org";
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuthService authService;
  @MockBean DrsApiFactory drsApiFactory;
  @MockBean ExternalCredsApiFactory externalCredsApiFactory;

  private final PassportProvider rasProvider = PassportProvider.RAS;

  @BeforeEach
  void before() {
    authService.clearCaches();
  }

  @Test
  void testCallsCorrectEndpointsWhenOnlyAccessUrlRequestedWithPassports() throws Exception {
    var cidProviderHost = getProviderHosts("passport");

    var drsObject = drsObjectWithRandomId("gs");

    mockDrsApiAccessUrlWithPassport(
        cidProviderHost.dnsHost(), drsObject, TEST_PASSPORT, "gs", TEST_ACCESS_URL);

    mockExternalcredsApi(rasProvider, TEST_ACCESS_TOKEN, Optional.of(TEST_PASSPORT));

    postDrsHubRequestAccessUrlSuccess(cidProviderHost, drsObject.getId());
  }

  @Test
  void testFallbackWhenOnlyAccessUrlRequestedWithPassportsHasEmptyPassport() throws Exception {
    var accessId = "gs";
    var cidProviderHost = getProviderHosts("passport");
    var drsObject = drsObjectWithRandomId("gs");

    var drsApi =
        mockDrsApiAccessUrlWithToken(
            cidProviderHost.dnsHost(), drsObject, accessId, TEST_ACCESS_URL);

    mockExternalcredsApi(rasProvider, TEST_ACCESS_TOKEN, Optional.empty());

    mockExternalCredsGetProviderAccessToken(
        Provider.fromValue(cidProviderHost.drsProvider().getEcmFenceProvider().get().getUriValue()),
        TEST_ACCESS_TOKEN,
            TEST_FENCE_SA_TOKEN);

    postDrsHubRequestAccessUrlSuccess(cidProviderHost, drsObject.getId());

    // need an extra verify because nothing in the mock cares that bearer token is set or not
    verify(drsApi).setBearerToken(TEST_FENCE_SA_TOKEN);
    // verify that the passport postAccessURL method was not called, since there is no passport
    verify(drsApi, never()).postAccessURL(any(), any(), any());
  }

  @Test
  void testFallbackWhenOnlyAccessUrlRequestedWithPassportsFails() throws Exception {
    var accessId = "gs";
    var cidProviderHost = getProviderHosts("passport");
    var drsObject = drsObjectWithRandomId("gs");

    var drsApi =
        mockDrsApiAccessUrlWithToken(
            cidProviderHost.dnsHost(), drsObject, accessId, TEST_ACCESS_URL);

    when(drsApi.postAccessURL(
            Map.of("passports", List.of(TEST_PASSPORT)), drsObject.getId(), accessId))
        .thenThrow(new RestClientException("Failed to retrieve access url with passport"));

    mockExternalcredsApi(rasProvider, TEST_ACCESS_TOKEN, Optional.of(TEST_PASSPORT));

    mockExternalCredsGetProviderAccessToken(
        Provider.fromValue(cidProviderHost.drsProvider().getEcmFenceProvider().get().getUriValue()),
        TEST_ACCESS_TOKEN,
            TEST_FENCE_SA_TOKEN);

    postDrsHubRequestAccessUrlSuccess(cidProviderHost, drsObject.getId());

    // verify that the passport postAccessURL method was called for the passport
    verify(drsApi)
        .postAccessURL(Map.of("passports", List.of(TEST_PASSPORT)), drsObject.getId(), accessId);
    // need an extra verify because nothing in the mock cares that bearer token is set or not
    verify(drsApi).setBearerToken(TEST_FENCE_SA_TOKEN);
  }

  @Test
  void testDoesNotFailWhenExtraDataSubmitted() throws Exception {
    var cidProviderHost = getProviderHosts("passport");
    var drsObject = drsObjectWithRandomId("gs");

    mockDrsApiAccessUrlWithToken(cidProviderHost.dnsHost(), drsObject, "gs", TEST_ACCESS_URL);

    mockExternalCredsGetProviderAccessToken(
        Provider.fromValue(cidProviderHost.drsProvider().getEcmFenceProvider().get().getUriValue()),
        TEST_ACCESS_TOKEN,
            TEST_FENCE_SA_TOKEN);

    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "url",
                String.format("drs://%s:%s", cidProviderHost.compactUriPrefix(), drsObject.getId()),
                "fields",
                List.of(Fields.CONTENT_TYPE),
                "foo",
                "bar"));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isOk());
  }

  @Test
  void testDoesNotCallEcmWhenOnlyDrsFieldsRequested() throws Exception {
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
                        .accessUrl(
                            (AllOfAccessMethodAccessUrl)
                                new AllOfAccessMethodAccessUrl().url("gs://foobar"))
                        .type(TypeEnum.GS)));

    mockDrsApi(host, drsObject);

    List<String> requestedFields =
        List.of(Fields.GS_URI, Fields.SIZE, Fields.HASHES, Fields.TIME_UPDATED, Fields.FILE_NAME);

    Map<String, Object> drsObjectMap = drsObjectToMap(drsObject, requestedFields);
    drsObjectMap.put(Fields.TIME_UPDATED, formatter.format(updatedTime.toInstant()));
    postDrsHubRequest(TEST_ACCESS_TOKEN, cid, drsObject.getId(), requestedFields)
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(drsObjectMap), true));

    verify(externalCredsApiFactory, times(0)).getApi(any());
  }

  @Test
  void testDrsProviderDoesNotSupportGoogle() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            UUID.randomUUID().toString(),
            List.of(Fields.GOOGLE_SERVICE_ACCOUNT))
        .andExpect(status().isOk())
        .andExpect(content().json("{}"));
  }

  @Test
  void testDrsProviderDoesSupportGoogle() throws Exception {
    var cidProviderHost = getProviderHosts("bioDataCatalyst");

    Map<String, Object> fenceAccountKey = new HashMap<>();
    fenceAccountKey.put("foo", "sa key");
    ObjectMapper mapper = new ObjectMapper();
    mockExternalCredsFenceAccountKeyApi(
        Provider.fromValue(cidProviderHost.drsProvider().getEcmFenceProvider().get().getUriValue()),
        TEST_ACCESS_TOKEN,
        mapper.writeValueAsString(fenceAccountKey));

    var saKeyObject = new SaKeyObject().data(fenceAccountKey);
    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            UUID.randomUUID().toString(),
            List.of(Fields.GOOGLE_SERVICE_ACCOUNT))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    objectMapper.writeValueAsString(
                        Map.of(Fields.GOOGLE_SERVICE_ACCOUNT, saKeyObject))));
  }

  @Test
  void testCallsCorrectEndpointsWhenOnlyAccessUrlRequested() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var drsObject = drsObjectWithRandomId("s3");

    var drsApi =
        mockDrsApiAccessUrlWithToken(cidProviderHost.dnsHost(), drsObject, "s3", TEST_ACCESS_URL);

    mockExternalCredsGetProviderAccessToken(
        Provider.fromValue(cidProviderHost.drsProvider().getEcmFenceProvider().get().getUriValue()),
        TEST_ACCESS_TOKEN,
            TEST_FENCE_SA_TOKEN);

    postDrsHubRequestAccessUrlSuccess(cidProviderHost, drsObject.getId());

    // need an extra verify because nothing in the mock cares that bearer token is set or not
    verify(drsApi).setBearerToken(TEST_FENCE_SA_TOKEN);
  }

  @Test
  void testEcmUnauthorized() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var drsObject = drsObjectWithRandomId("s3");

    var drsApi =
        mockDrsApiAccessUrlWithToken(cidProviderHost.dnsHost(), drsObject, "s3", TEST_ACCESS_URL);

    mockExternalCredsGetProviderAccessTokenError(
        Provider.fromValue(cidProviderHost.drsProvider().getEcmFenceProvider().get().getUriValue()),
        TEST_ACCESS_TOKEN,
        HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED, "", HttpHeaders.EMPTY, null, null));

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            drsObject.getId(),
            List.of(Fields.ACCESS_URL))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testEcmNotFound() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var drsObject = drsObjectWithRandomId("s3");

    var drsApi =
        mockDrsApiAccessUrlWithToken(cidProviderHost.dnsHost(), drsObject, "s3", TEST_ACCESS_URL);

    mockExternalCredsGetProviderAccessTokenError(
        Provider.fromValue(cidProviderHost.drsProvider().getEcmFenceProvider().get().getUriValue()),
        TEST_ACCESS_TOKEN,
        HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", HttpHeaders.EMPTY, null, null));

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            drsObject.getId(),
            List.of(Fields.ACCESS_URL))
        .andExpect(status().isNotFound());
  }

  @Test
  void testForceFetchAccessUrlAllProviders() throws Exception {
    var providersList =
        config.getDrsProviders().entrySet().stream()
            .filter(
                p ->
                    p.getValue().getAccessMethodConfigs().stream()
                        .anyMatch(c -> !c.isFetchAccessUrl()))
            .map(Entry::getKey)
            .toList();

    for (var providerName : providersList) {
      var cidProviderHost = getProviderHosts(providerName);

      var accessMethod =
          cidProviderHost.drsProvider().getAccessMethodConfigs().stream()
              .filter(c -> !c.isFetchAccessUrl())
              .findAny()
              .get()
              .getType()
              .getReturnedEquivalent()
              .toString();

      var drsObject = drsObjectWithRandomId(accessMethod);

      mockDrsApiAccessUrlWithToken(
          cidProviderHost.dnsHost(), drsObject, accessMethod, TEST_ACCESS_URL);

      cidProviderHost
          .drsProvider()
          .getEcmFenceProvider()
          .ifPresent(
              p ->
                  mockExternalCredsGetProviderAccessToken(
                      Provider.fromValue(p.toString()), TEST_ACCESS_TOKEN, TEST_FENCE_SA_TOKEN));

      mockExternalcredsApi(rasProvider, TEST_ACCESS_TOKEN, Optional.of(TEST_PASSPORT));

      mvc.perform(
              post("/api/v4/drs/resolve")
                  .header("authorization", "bearer " + TEST_ACCESS_TOKEN)
                  .header("drshub-force-access-url", "true")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "url",
                              String.format(
                                  "drs://%s:%s",
                                  cidProviderHost.compactUriPrefix(), drsObject.getId()),
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

  @Test
  void testUsesProvidedFilename() throws Exception {
    var cidProviderHost = getProviderHosts("bioDataCatalyst");

    var fileName = "foo.bar.txt";
    var drsObject = drsObjectWithRandomId("gs").name(fileName);
    drsObject
        .getAccessMethods()
        .get(0)
        .setAccessUrl(
            (AllOfAccessMethodAccessUrl)
                new AllOfAccessMethodAccessUrl().url("gs://bucket/bad.different.name.txt"));

    mockDrsApi(cidProviderHost.dnsHost(), drsObject);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            drsObject.getId(),
            List.of(Fields.FILE_NAME))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(objectMapper.writeValueAsString(Map.of(Fields.FILE_NAME, fileName)), true));
  }

  @Test
  void testParsesMissingFilenameFromAccessUrl() throws Exception {
    var cidProviderHost = getProviderHosts("bioDataCatalyst");

    var drsObject = drsObjectWithRandomId("gs");
    var fileName = "foo.bar.txt";
    drsObject
        .getAccessMethods()
        .get(0)
        .setAccessUrl(
            (AllOfAccessMethodAccessUrl)
                new AllOfAccessMethodAccessUrl().url("gs://bucket/" + fileName));

    mockDrsApi(cidProviderHost.dnsHost(), drsObject);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            drsObject.getId(),
            List.of(Fields.FILE_NAME))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(objectMapper.writeValueAsString(Map.of(Fields.FILE_NAME, fileName)), true));
  }

  @Test
  void testReturns400WhenNoFieldsRequested() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", cidProviderHost.compactUriPrefix(), "fields", List.of("")));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test
  void testReturns400IfFieldsIsNotAList() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", cidProviderHost.compactUriPrefix(), "fields", "not a list"));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test
  void testReturns400IfInvalidFieldRequested() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "url",
                cidProviderHost.compactUriPrefix(),
                "fields",
                List.of("fake field", "fake field 2")));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test
  void testReturns401IfNoAuthHeaderIsSent() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "url", cidProviderHost.compactUriPrefix(), "fields", List.of(Fields.CONTENT_TYPE)));

    mvc.perform(
            post("/api/v4/drs/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testReturns400IfNotGivenUrl() throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("notAUrl", "drs://foo/bar", "fields", List.of(Fields.CONTENT_TYPE)));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test
  void testReturns400IfGivenDgUrlWithoutPath() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "url", cidProviderHost.compactUriPrefix(), "fields", List.of(Fields.CONTENT_TYPE)));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test
  void testShouldReturn400IfGivenDgUrlWithOnlyPath() throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", UUID.randomUUID(), "fields", List.of(Fields.CONTENT_TYPE)));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test
  void testShouldReturn400IfNoDataPostedWithRequest() throws Exception {
    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, "").andExpect(status().isBadRequest());
  }

  @Test
  void testReturns400IfGivenInvalidUrlValue() throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of("url", "I am not a url", "fields", List.of(Fields.CONTENT_TYPE)));

    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isBadRequest());
  }

  @Test
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

  @Test
  void testReturns500IfKeyRetrievalFromEcmFails() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var drsObject = drsObjectWithRandomId("s3");

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            drsObject.getId(),
            List.of(Fields.ACCESS_URL))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void testShouldReturnUnderlyingStatusIfGettingAccessUrlFails() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");
    var drsObject = drsObjectWithRandomId("s3");

    when(mockDrsApi(cidProviderHost.dnsHost(), drsObject).getAccessURL(drsObject.getId(), "s3"))
        .thenThrow(new HttpServerErrorException(HttpStatus.NOT_IMPLEMENTED, "forced sad response"));
    mockExternalCredsGetProviderAccessToken(
        Provider.fromValue(cidProviderHost.drsProvider().getEcmFenceProvider().get().getUriValue()),
        TEST_ACCESS_TOKEN,
            TEST_FENCE_SA_TOKEN);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            drsObject.getId(),
            List.of(Fields.ACCESS_URL))
        .andExpect(status().is(HttpStatus.NOT_IMPLEMENTED.value()));
  }

  @Test
  void testReturnsNullForFieldsMissingInDrsResponse() throws Exception {
    var cidProviderHost = getProviderHosts("kidsFirst");

    var formatter = DateTimeFormatter.ISO_INSTANT;
    var createdTime = new Date(123);
    var drsObject = drsObjectWithRandomId("gs").createdTime(createdTime);
    List<String> requestedFields = List.of(Fields.TIME_CREATED, Fields.LOCALIZATION_PATH);

    var expectedMap = new HashMap<String, Object>();
    expectedMap.put(Fields.TIME_CREATED, formatter.format(createdTime.toInstant()));
    expectedMap.put(Fields.LOCALIZATION_PATH, null);

    mockDrsApi(cidProviderHost.dnsHost(), drsObject);

    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            drsObject.getId(),
            requestedFields)
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(expectedMap), true));
  }

  @Test
  void testHandlesUnknownCid() throws Exception {
    postDrsHubRequest(TEST_ACCESS_TOKEN, "dg.fake", "12345", List.of(Fields.NAME))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testHandlesUnknownHost() throws Exception {
    postDrsHubRequest(TEST_ACCESS_TOKEN, "badhost.com", "12345", List.of(Fields.NAME))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testHandlesUnparsableHost() throws Exception {
    postDrsHubRequest(TEST_ACCESS_TOKEN, "?", "12345", List.of(Fields.NAME))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testHandlesQueryString() throws Exception {
    postDrsHubRequest(TEST_ACCESS_TOKEN, TDR_TEST_HOST, "12345?foo=bar", List.of(Fields.NAME))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testHandleSlashInDrsObjectId() throws Exception {
    var drsHost = TDR_TEST_HOST;
    var drsObject = drsObjectWithId("foo/bar", "gs");
    var expectedDrsUri = "drs://" + TDR_TEST_HOST + "/foo%2Fbar";
    var requestBody = objectMapper.writeValueAsString(Map.of("url", expectedDrsUri));

    mockDrsApiRestTemplate(drsHost, drsObject);
    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isOk());
  }

  @Test
  void testHandleEncodedSlashInDrsObjectId() throws Exception {
    var drsHost = TDR_TEST_HOST;
    var drsObject = drsObjectWithId("foo%2Fbar", "gs");
    var expectedDrsUri = "drs://" + TDR_TEST_HOST + "/foo%2Fbar";
    var requestBody = objectMapper.writeValueAsString(Map.of("url", expectedDrsUri));

    mockDrsApiRestTemplate(drsHost, drsObject);
    postDrsHubRequestRaw(TEST_ACCESS_TOKEN, requestBody).andExpect(status().isOk());
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

  private void postDrsHubRequestAccessUrlSuccess(ProviderHosts cidProviderHost, String drsObjectId)
      throws Exception {
    postDrsHubRequest(
            TEST_ACCESS_TOKEN,
            cidProviderHost.compactUriPrefix(),
            drsObjectId,
            List.of(Fields.ACCESS_URL))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    objectMapper.writeValueAsString(Map.of(Fields.ACCESS_URL, TEST_ACCESS_URL)),
                    true));
  }

  private ResultActions postDrsHubRequest(
      String accessToken, String compactIdPrefix, String objectId, List<String> fields)
      throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(
            Map.of(
                "url", String.format("drs://%s:%s", compactIdPrefix, objectId), "fields", fields));

    return postDrsHubRequestRaw(accessToken, requestBody);
  }

  private ResultActions postDrsHubRequestRaw(String accessToken, String requestBody)
      throws Exception {
    return mvc.perform(
        post("/api/v4/drs/resolve")
            .header("authorization", "bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
  }

  private OauthApi mockExternalCredsGetProviderAccessToken(
      Provider fenceProvider, String accessToken, String fenceProviderToken) {
    var mockExternalCredsOauthApi = mock(OauthApi.class);
    when(externalCredsApiFactory.getOauthApi(accessToken)).thenReturn(mockExternalCredsOauthApi);
    when(mockExternalCredsOauthApi.getProviderAccessToken(fenceProvider))
        .thenReturn(fenceProviderToken);
    return mockExternalCredsOauthApi;
  }

  private OauthApi mockExternalCredsGetProviderAccessTokenError(
      Provider fenceProvider, String accessToken, RestClientException exception) {
    var mockExternalCredsOauthApi = mock(OauthApi.class);
    when(externalCredsApiFactory.getOauthApi(accessToken)).thenReturn(mockExternalCredsOauthApi);
    when(mockExternalCredsOauthApi.getProviderAccessToken(fenceProvider)).thenThrow(exception);
    return mockExternalCredsOauthApi;
  }

  private FenceAccountKeyApi mockExternalCredsFenceAccountKeyApi(
      Provider fenceProvider, String accessToken, String fenceAccountKey) {
    var mockExternalCredsFenceAccountKeyApi = mock(FenceAccountKeyApi.class);
    when(externalCredsApiFactory.getFenceAccountKeyApi(accessToken))
        .thenReturn(mockExternalCredsFenceAccountKeyApi);
    ObjectMapper mapper = new ObjectMapper();

    when(mockExternalCredsFenceAccountKeyApi.getFenceAccountKey(fenceProvider))
        .thenReturn(fenceAccountKey);
    return mockExternalCredsFenceAccountKeyApi;
  }

  private OidcApi mockExternalcredsApi(
      PassportProvider passportProvider, String accessToken, Optional<String> passport) {
    var mockExternalCredsApi = mock(OidcApi.class);
    when(externalCredsApiFactory.getOidcApi(accessToken)).thenReturn(mockExternalCredsApi);
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

    // ObjectIds are decoded and re-encoded when sent to the Drs server
    var effectiveObjectId =
        Optional.ofNullable(drsObject.getId())
            .map(objectId -> URLDecoder.decode(objectId, StandardCharsets.UTF_8))
            .map(objectId -> URLEncoder.encode(objectId, StandardCharsets.UTF_8))
            .orElse("");

    when(drsApiFactory.getApiFromUriComponents(
            eq(
                UriComponentsBuilder.newInstance()
                    .scheme("drs")
                    .host(drsHost)
                    .path(effectiveObjectId)
                    .build()),
            any()))
        .thenReturn(mockDrsApi);
    when(mockDrsApi.getObject(drsObject.getId(), null)).thenReturn(drsObject);

    return mockDrsApi;
  }

  private DrsApi mockDrsApiRestTemplate(String drsHost, DrsObject drsObject) {
    var restTemplate = mock(RestTemplate.class);
    var apiClient = new ApiClient(restTemplate);
    apiClient.setBasePath(
        apiClient.getBasePath().replace("{serverURL}", Objects.requireNonNull(drsHost)));
    var mockDrsApi = new DrsApi(apiClient); // mock(DrsApi.class);

    // ObjectIds are decoded and re-encoded when sent to the Drs server
    var effectiveObjectId =
        Optional.ofNullable(drsObject.getId())
            .map(objectId -> URLDecoder.decode(objectId, StandardCharsets.UTF_8))
            .map(objectId -> URLEncoder.encode(objectId, StandardCharsets.UTF_8))
            .orElse("");

    when(drsApiFactory.getApiFromUriComponents(
            eq(
                UriComponentsBuilder.newInstance()
                    .scheme("drs")
                    .host(drsHost)
                    .path(effectiveObjectId)
                    .build()),
            any()))
        .thenReturn(mockDrsApi);

    // Mock the Options endpoint
    when(restTemplate.exchange(any(), eq(new ParameterizedTypeReference<Authorizations>() {})))
        .thenAnswer(
            a -> {
              RequestEntity<Authorizations> request = a.getArgument(0);
              var drsServerUrl =
                  "https://" + drsHost + "/ga4gh/drs/v1/objects/" + effectiveObjectId;
              if (request.getUrl().toString().equalsIgnoreCase(drsServerUrl)) {
                return ResponseEntity.ok(
                    new Authorizations().supportedTypes(List.of(SupportedTypesEnum.BEARERAUTH)));
              } else {
                return ResponseEntity.notFound();
              }
            });

    // Mock the DrsObject retrieval endpoint
    when(restTemplate.exchange(any(), eq(new ParameterizedTypeReference<DrsObject>() {})))
        .thenAnswer(
            a -> {
              RequestEntity<DrsObject> request = a.getArgument(0);
              var drsServerUrl =
                  "https://" + drsHost + "/ga4gh/drs/v1/objects/" + effectiveObjectId;
              if (request.getUrl().toString().equalsIgnoreCase(drsServerUrl)) {
                return ResponseEntity.ok(drsObject);
              } else {
                return ResponseEntity.notFound();
              }
            });

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

  private DrsObject drsObjectWithRandomId(String accessMethod) {
    return drsObjectWithId(UUID.randomUUID().toString(), accessMethod);
  }

  private DrsObject drsObjectWithId(String objectId, String accessMethod) {
    return new DrsObject()
        .id(objectId)
        .size(new Random().nextLong())
        .accessMethods(
            List.of(
                new AccessMethod().accessId(accessMethod).type(TypeEnum.fromValue(accessMethod))));
  }
}
