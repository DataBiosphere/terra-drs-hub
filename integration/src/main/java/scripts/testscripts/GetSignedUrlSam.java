package scripts.testscripts;

import bio.terra.drshub.api.GcsApi;
import bio.terra.drshub.model.GetSignedUrlRequest;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import scripts.utils.ClientTestUtils;

@Slf4j
public class GetSignedUrlSam extends TestScript {
  private static String TDR_TEST_DRS_URI =
      "drs://jade.datarepo-dev.broadinstitute.org/v1_93dc1e76-8f1c-4949-8f9b-07a087f3ce7b_8b07563a-542f-4b5c-9e00-e8fe6b1861de";

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    var apiClient = ClientTestUtils.getClientWithTestUserAuth(testUser, server);
    var gcsApi = new GcsApi(apiClient);

    var request =
        new GetSignedUrlRequest()
            .bucket("broad-jade-dev-data-bucket")
            .object("fd8d8492-ad02-447d-b54e-35a7ffd0e7a5/8b07563a-542f-4b5c-9e00-e8fe6b1861de")
            .dataObjectUri(TDR_TEST_DRS_URI)
            .googleProject("terra-dev-149e1673");

    var signedUrl = gcsApi.getSignedUrl(request);
    Assertions.assertTrue(signedUrl.contains("broad-jade-dev-data-bucket"));
  }
}
