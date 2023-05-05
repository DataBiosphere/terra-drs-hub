package bio.terra.drshub.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;
import bio.terra.drshub.models.DrsHubAuthorization;
import bio.terra.drshub.services.AuthService;
import bio.terra.drshub.services.DrsResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.Authorizations;
import io.github.ga4gh.drs.model.Authorizations.SupportedTypesEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;

@WebMvcTest
@ContextConfiguration(classes = {DrsHubApiController.class})
@Provider("drshub")
@PactBroker()
class VerifyPactsDrsHubApiController {

  @MockBean private AuthService authService;
  @MockBean private DrsResolutionService drsResolutionService;
  @Autowired private ObjectMapper objectMapper;

  // This mockMVC is what we use to test API requests and responses:
  @Autowired private MockMvc mockMvc;

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
  public void checkStatusEndpoint() throws Exception {

    when(authService.buildAuthorizations(any(), any(), any())).thenReturn(List.of(new DrsHubAuthorization(
        SupportedTypesEnum.BEARERAUTH, this::getAuthForAccessMethodType)));

    when(drsResolutionService.resolveDrsObject(any(), any(), any(), any(), any()))
        .thenReturn(new bio.terra.drshub.models.ResourceMetadata());

  }

  @State({"resolve Drs url"})
  public void resolveDrsUrl() throws Exception {

  }


}
