package bio.terra.drshub.config;

import java.util.Map;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface DrsHubConfigInterface {

  String getBondUrl();

  String getSamUrl();

  String getExternalcredsUrl();

  Map<String, String> getCompactIdHosts();

  Map<String, DrsProvider> getDrsProviders();
}
