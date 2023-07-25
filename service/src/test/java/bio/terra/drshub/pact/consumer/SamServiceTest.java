package bio.terra.drshub.pact.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.services.SamApiFactory;
import bio.terra.profile.app.configuration.SamConfiguration;
import bio.terra.profile.service.iam.SamService;
import java.util.HashMap;

import bio.terra.sam.model.ProjectSignedUrlForBlobBody;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("pact-test")
@PactConsumerTest
public class SamServiceTest {

  @Pact(consumer = "drshub-consumer", provider = "sam-provider")
  public RequestResponsePact signedUrlApiPact(PactDslWithProvider builder) {
    var projectId = "terra-abcd1234";
    var signedUrlResponse = new PactDslJsonBody();
    signedUrlResponse.setBody(new StringValue("gs://mybucket/myobject.txt"));
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
  @PactTestFor(pactMethod = "signedUrlApiPact", pactVersion = PactSpecVersion.V3)
  public void testSamSignedUrlApi(MockServer mockServer) {
    var drsHubConfig = DrsHubConfig.create().setSamUrl(mockServer.getUrl());
    var samApiFactory = new SamApiFactory(drsHubConfig);
    var samApi = samApiFactory.getApi(new BearerToken("test-token"));
    var request = new ProjectSignedUrlForBlobBody()
        .bucketName("mybucket")
        .blobName("myobject.txt")
        .requesterPays(false);
    var response = samApi.signedUrlForBlob(request, "terra-abcd1234");
    assertTrue(response.contains("mybucket/myobject.txt"));
  }
}
