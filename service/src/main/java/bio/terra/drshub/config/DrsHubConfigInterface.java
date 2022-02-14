package bio.terra.drshub.config;

import java.util.ArrayList;
import java.util.Map;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface DrsHubConfigInterface {

  String getBondUrl();

  String getSamUrl();

  String getExternalcredsUrl();

  Map<String, String> getHosts();

  ArrayList<DrsProvider> getDrsProviders();
}
