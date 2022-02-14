package bio.terra.drshub;

import java.util.Map;

public class Config {

  private String currentEnv;

  public final String ENV_MOCK = "mock";
  public final String ENV_DEV = "dev";
  public final String ENV_PROD = "prod";
  public final String ENV_CROMWELL_DEV = "cromwell-dev";

  public final String HOST_MOCK_DRS = "wb-mock-drs-dev.storage.googleapis.com";
  public final String HOST_BIODATA_CATALYST_PROD = "gen3.biodatacatalyst.nhlbi.nih.gov";
  public final String HOST_BIODATA_CATALYST_STAGING = "staging.gen3.biodatacatalyst.nhlbi.nih.gov";
  public final String HOST_CRDC_PROD = "nci-crdc.datacommons.io";
  public final String HOST_CRDC_STAGING = "nci-crdc-staging.datacommons.io";
  public final String HOST_KIDS_FIRST_PROD = "data.kidsfirstdrc.org";
  public final String HOST_KIDS_FIRST_STAGING = "gen3staging.kidsfirstdrc.org";
  public final String HOST_TDR_DEV = "jade.datarepo-dev.broadinstitute.org";
  public final String HOST_THE_ANVIL_PROD = "gen3.theanvil.io";
  public final String HOST_THE_ANVIL_STAGING = "staging.theanvil.io";

  private String terraEnvFrom(String drshubEnv) {
    var lowerDrshubEnv = drshubEnv.toLowerCase();
    switch (lowerDrshubEnv) {
      case ENV_MOCK:
      case ENV_CROMWELL_DEV:
      case ENV_DEV:
        return ENV_DEV;
      default:
        return lowerDrshubEnv;
    }
  }

  /**
   * Return a configuration object with default values for the specified Drshub and DSDE
   * environments.
   *
   * @param drshubEnv {string} Drshub environment (mock, dev, prod etc.)
   * @param dsdeEnv {string} The DSDE environment (qa, staging, dev, prod etc.)
   * @return {{theAnvilHost: (string), crdcHost: (string), kidsFirstHost: (string), bondBaseUrl:
   *     string, itDrshubBaseUrl: string, itBondBaseUrl: string, samBaseUrl: string,
   *     bioDataCatalystHost: (string)}}
   */
  private Map<String, String> defaultsForEnv(String drshubEnv, String dsdeEnv) {

    return Map.of(
        "theAnvilHost", "",
        "crdcHost", "",
        "kidsFirstHost", "",
        "bondBaseUrl", "",
        "itDrshubBaseUrl", "",
        "itBondBaseUrl", "",
        "samBaseUrl", "",
        "bioDataCatalystHost", "");
  }
}
