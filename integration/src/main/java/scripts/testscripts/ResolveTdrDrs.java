package scripts.testscripts;

import bio.terra.drshub.api.DrsHubApi;
import bio.terra.drshub.model.RequestObject;
import bio.terra.drshub.model.ResourceMetadata;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import scripts.utils.ClientTestUtils;

@Slf4j
public class ResolveTdrDrs extends TestScript {
  private static String TDR_TEST_DRS_URI =
      "drs://jade.datarepo-dev.broadinstitute.org/v1_93dc1e76-8f1c-4949-8f9b-07a087f3ce7b_8b07563a-542f-4b5c-9e00-e8fe6b1861de";

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    var apiClient = ClientTestUtils.getClientWithTestUserAuth(testUser, server);
    var drsHubApi = new DrsHubApi(apiClient);

    var actualMetadata = drsHubApi.resolveDrs(new RequestObject().url(TDR_TEST_DRS_URI));
    var expectedMetadata =
        new ResourceMetadata()
            .contentType("application/octet-stream")
            .size(15601108255L)
            .timeCreated(new Date(1588002969696L)) // 2020-04-27T15:56:09.696Z
            .timeUpdated(new Date(1588002969696L)) // 2020-04-27T15:56:09.696Z
            .bucket("broad-jade-dev-data-bucket")
            .name("fd8d8492-ad02-447d-b54e-35a7ffd0e7a5/8b07563a-542f-4b5c-9e00-e8fe6b1861de")
            .gsUri(
                "gs://broad-jade-dev-data-bucket/fd8d8492-ad02-447d-b54e-35a7ffd0e7a5/8b07563a-542f-4b5c-9e00-e8fe6b1861de")
            .googleServiceAccount(null)
            .fileName("HG00096.mapped.ILLUMINA.bwa.GBR.low_coverage.20120522.bam")
            .localizationPath(
                "/1000GenomesDataset/bam_files/HG00096.mapped.ILLUMINA.bwa.GBR.low_coverage.20120522.bam")
            .hashes(Map.of("md5", "336ea55913bc261b72875bd259753046", "crc32c", "ecb19226"));

    Assertions.assertEquals(expectedMetadata, actualMetadata);
  }
}
