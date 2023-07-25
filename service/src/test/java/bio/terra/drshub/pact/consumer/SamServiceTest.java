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
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.services.SamApiFactory;

import bio.terra.sam.model.ProjectSignedUrlForBlobBody;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("pact-test")
@PactConsumerTest
public class SamServiceTest {

  @Pact(consumer = "drshub-consumer", provider = "sam-provider")
  public RequestResponsePact signedUrlApiPact(PactDslWithProvider builder) {
    return builder
        .given("A signed URL request")
        .uponReceiving("a request for a signed URL")
        .matchPath("/api/google/v1/user/petServiceAccount/terra(-|-dev-|-alpha-|-qa-|-staging-|)[A-Fa-f0-9]{8}/signedUrlForBlob", "/api/google/v1/user/petServiceAccount/terra-abcd1234/signedUrlForBlob")
        .method("POST")
        .willRespondWith()
        .status(200)
        .body(PactDslJsonRootValue.stringType())
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
