package bio.terra.drshub.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;
import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.config.ProviderAccessMethodConfig;
import bio.terra.drshub.logging.AuditLogger;
import bio.terra.drshub.models.AccessMethodConfigTypeEnum;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.BondProviderEnum;
import bio.terra.drshub.models.DrsApi;
import bio.terra.drshub.models.DrsHubAuthorization;
import bio.terra.drshub.services.AuthService;
import bio.terra.drshub.services.DrsApiFactory;
import bio.terra.drshub.services.DrsProviderService;
import bio.terra.drshub.services.DrsResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.Authorizations.SupportedTypesEnum;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@Tag("Pact")
@WebMvcTest
@ContextConfiguration(classes = {DrsHubApiController.class, PublicApiController.class})
@Provider("drshub-provider")
@PactBroker()
class VerifyPactsDrsHubApiController {

  @MockBean private DrsHubConfig drsHubConfig;
  @MockBean private BearerTokenFactory tokenFactory;
  @MockBean private AuthService authService;
  @MockBean private DrsApi drsApi;
  @MockBean private DrsApiFactory drsApiFactory;
  @MockBean private AuditLogger auditLogger;
  @SpyBean private DrsResolutionService drsResolutionService;
  @SpyBean private DrsProviderService drsProviderService;

  @Autowired private ObjectMapper objectMapper;

  // This mockMVC is what we use to test API requests and responses:
  @Autowired private MockMvc mockMvc;

  SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

  @TestTemplate
  @ExtendWith(PactVerificationSpringProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context) {
    context.verifyInteraction();
  }

  @BeforeEach
  void before(PactVerificationContext context) {
    context.setTarget(new MockMvcTestTarget(mockMvc));
  }

  Optional<List<String>> getAuthForAccessMethodType(AccessMethod.TypeEnum accessMethodType) {
    return Optional.of(List.of("Bearer: test 123"));
  }

  @State({"Drshub is ok"})
  public void checkStatusEndpoint() throws Exception {}

  @State({"resolve Drs url"})
  public void resolveDrsUrl(Map<String, String> providerStateParams) throws Exception {
    when(authService.buildAuthorizations(any(), any(), any()))
        .thenReturn(
            List.of(
                new DrsHubAuthorization(
                    SupportedTypesEnum.PASSPORTAUTH, this::getAuthForAccessMethodType)));

    when(authService.fetchUserServiceAccount(any(), any())).thenReturn(null);

    when(drsApi.getObject(any(), any())).thenReturn(null);

    var drsProvider = DrsProvider.create();
    drsProvider.setHostRegex(".*\\.theanvil\\.io");
    drsProvider.setMetadataAuth(false);
    drsProvider.setBondProvider(BondProviderEnum.anvil);
    drsProvider.setUseAliasesForLocalizationPath(true);
    drsProvider.setName("AnVIL");

    var accessMethodConfig = ProviderAccessMethodConfig.create();
    accessMethodConfig.setAuth(AccessUrlAuthEnum.passport);
    accessMethodConfig.setType(AccessMethodConfigTypeEnum.gs);
    accessMethodConfig.setFetchAccessUrl(true);

    var accessMethodConfigs = new ArrayList<ProviderAccessMethodConfig>();
    accessMethodConfigs.add(accessMethodConfig);
    drsProvider.setAccessMethodConfigs(accessMethodConfigs);
    drsProvider.setName(providerStateParams.get("bondProvider"));

    when(drsHubConfig.getDrsProviders()).thenReturn(Map.of("anvil", drsProvider));

    when(drsApiFactory.getApiFromUriComponents(any(), any())).thenReturn(drsApi);
    var drsObject =
        new io.github.ga4gh.drs.model.DrsObject()
            .id("1234567890")
            .checksums(
                List.of(
                    new io.github.ga4gh.drs.model.Checksum()
                        .checksum(providerStateParams.get("fileHash"))
                        .type("md5")))
            .createdTime(Date.from(Instant.now()))
            .description("test")
            .mimeType("application/json")
            .size(Long.parseLong(providerStateParams.get("fileSize")))
            .updatedTime(dateFormat.parse(providerStateParams.get("timeCreated")))
            .createdTime(dateFormat.parse(providerStateParams.get("timeCreated")))
            .accessMethods(
                List.of(
                    new AccessMethod()
                        .accessId(providerStateParams.get("fileId"))
                        .type(AccessMethod.TypeEnum.GS)))
            .version("1.0");

    when(drsApi.getObject(any(), any())).thenReturn(drsObject);

    var accessUrl =
        new AccessURL()
            .url(providerStateParams.get("accessUrl"))
            .headers(List.of("Header", "Example"));

    when(drsApi.postAccessURL(any(), any(), any())).thenReturn(accessUrl);
  }
}
