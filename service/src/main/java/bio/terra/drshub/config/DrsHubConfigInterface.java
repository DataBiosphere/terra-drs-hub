package bio.terra.drshub.config;

import java.time.Duration;
import java.util.Map;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface DrsHubConfigInterface {

  String getBardUrl();

  String getBondUrl();

  String getSamUrl();

  String getExternalcredsUrl();

  Map<String, String> getCompactIdHosts();

  Map<String, DrsProvider> getDrsProviders();

  Duration getSignedUrlDuration();

  // Nullable to make the generated class play nicely with spring: spring likes to call the getter
  // before the setter and without Nullable the immutables generated code errors because the field
  // is not set yet. Spring does not seem to recognize Optional.
  @Nullable
  VersionProperties getVersion();

  Integer getPencilsDownSeconds();

  Integer asyncThreads();

  // If this is true, then we will track calls to the Bard API in MixPanel in addition to the
  // BigQuery Data warehouse.
  Boolean trackInMixPanel();

  // Only log events to Bard when enabled: this is exposed to help debug performance regressions
  // following the introduction of Bard logging.
  Boolean bardEventLoggingEnabled();
}
