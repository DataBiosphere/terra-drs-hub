package bio.terra.drshub;

import java.net.URL;
import java.util.regex.Pattern;

public class Helpers {

  private static final String dataGuidsHostPrefix = "dg.";
  private static final String dosDataObjectPathPrefix = "/ga4gh/dos/v1/dataobjects/";
  private static final String drsDataObjectPathPrefix = "/ga4gh/drs/v1/objects/";
  private static final Pattern jadeDataRepoHostRegex =
      Pattern.compile(".*data.*[-.](broadinstitute\\.org|terra\\.bio)$");
  private static final Pattern pathSlashRegex = Pattern.compile("^/?([^/]+.*?)/?$");

  private static final String PROD_DATASTAGE_NAMESPACE = "dg.4503";
  private static final String STAGING_DATASTAGE_NAMESPACE = "dg.712c";
  private static final String ANVIL_NAMESPACE = "dg.anv0";
  private static final String CRDC_NAMESPACE = "dg.4dfc";
  private static final String KIDS_FIRST_NAMESPACE = "dg.f82a1a";

  public static Boolean hasDataGuidsHost(URL someUrl) {
    return someUrl.getHost().startsWith(dataGuidsHostPrefix);
  }

  public static Boolean hasJadeDataRepoHost(URL someUrl) {
    return jadeDataRepoHostRegex.matcher(someUrl.getHost()).matches();
  }

  public static Boolean isDataGuidsUrl(URL someUrl) {
    return hasDataGuidsHost(someUrl)
        || (!someUrl.getHost().isEmpty() && someUrl.getPath().isEmpty());
  }

  public static void validateDataObjectUrl(URL someUrl) throws Exception {
    if ((hasDataGuidsHost(someUrl) || hasJadeDataRepoHost(someUrl))
        && someUrl.getPath().isEmpty()) {
      throw new Exception(
          String.format(
              "Data Object URIs with either '%s' or '%s' as host are required to have a path: '%s'",
              dataGuidsHostPrefix, jadeDataRepoHostRegex, someUrl));
    }
  }

  /*function validateDataObjectUrl(someUrl) {
    if ((hasDataGuidsHost(someUrl) || hasJadeDataRepoHost(someUrl)) && !someUrl.pathname) {
      throw new Error(`Data Object URIs with either '${dataGuidsHostPrefix}*' or '${jadeDataRepoHostRegex}' as host are required to have a path: "${url.format(someUrl)}"`);
    }
  }*/
}
