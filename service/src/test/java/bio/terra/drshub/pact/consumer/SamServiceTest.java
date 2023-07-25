package bio.terra.drshub.pact.consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslJsonRootValue;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.support.json.JsonValue;
import au.com.dius.pact.core.support.json.JsonValue.StringValue;
import bio.terra.profile.app.configuration.SamConfiguration;
import bio.terra.profile.service.iam.SamService;
import java.util.HashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("pact-test")
@PactConsumerTest
public class SamServiceTest {

  @Pact(consumer = "drshub-consumer", provider = "sam-provider")
  public RequestResponsePact signedUrlApiPact(PactDslWithProvider builder) {
    var projectId = "terra-abcd1234";
    var signedUrlResponse = new PactDslJsonBody();
    signedUrlResponse.setBody(new StringValue("gs//mybucket/myobject.txt"));
    var stateParams = new HashMap<String, String>();
    stateParams.put("projectId", projectId);
    return builder
        .given("A signed URL request", stateParams)
        .uponReceiving("a request for a signed URL")
        .pathFromProviderState("/api/google/v1/user/petServiceAccount/${projectId}/signedUrlForBlob", "/api/google/v1/user/petServiceAccount/" + projectId + "/signedUrlForBlob")
        .method("POST")
        .willRespondWith()
        .status(200)
        .body(signedUrlResponse)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "statusApiPact", pactVersion = PactSpecVersion.V3)
  public void testSamServiceStatusCheck(MockServer mockServer) {
    SamConfiguration config = new SamConfiguration(mockServer.getUrl(), "test@test.com");
    var samService = new SamService(config);
    var system = samService.status();
    assertTrue(system.isOk());

    // we could also assert that any subsystems we care about here are present
    // but then we'd need to add them to the pact above
    // system.getSystems()
  }

  @Test
  @PactTestFor(pactMethod = "userStatusPact", pactVersion = PactSpecVersion.V3)
  public void testSamServiceUserStatusInfo(MockServer mockServer) throws Exception {
    SamConfiguration config = new SamConfiguration(mockServer.getUrl(), "test@test.com");
    var samService = new SamService(config);
    samService.getUserStatusInfo("accessToken");
  }
}
