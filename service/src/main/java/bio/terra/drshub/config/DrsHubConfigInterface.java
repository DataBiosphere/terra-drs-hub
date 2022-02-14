package bio.terra.drshub.config;

import java.util.Map;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface DrsHubConfigInterface {

  String DG_COMPACT_BDC_PROD = "dg.4503";
  String DG_COMPACT_BDC_STAGING = "dg.712c";
  String DG_COMPACT_THE_ANVIL = "dg.anv0";
  String DG_COMPACT_CRDC = "dg.4dfc";
  String DG_COMPACT_KIDS_FIRST = "dg.f82a1a";
  String DG_COMPACT_PASSPORT_TEST = "dg.test0";

  String getBondUrl();

  String getSamUrl();

  String getExternalcredsUrl();

  Map<String, String> getHosts();

  Map<String, DrsProvider> getDrsProviders();

  default String expandCibHost(String cibHost) {
    switch (cibHost) {
      case DG_COMPACT_THE_ANVIL:
        return getHosts().get("theAnvil");
      case DG_COMPACT_CRDC:
        return getHosts().get("crdc");
      case DG_COMPACT_KIDS_FIRST:
        return getHosts().get("kidsFirst");
      case DG_COMPACT_BDC_STAGING:
      case DG_COMPACT_BDC_PROD:
        return getHosts().get("bioDataCatalyst");
      case DG_COMPACT_PASSPORT_TEST:
        return getHosts().get("passportTest");
    }
    return null;
  }
}
