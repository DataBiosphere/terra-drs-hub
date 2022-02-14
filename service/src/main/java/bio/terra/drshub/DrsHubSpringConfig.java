package bio.terra.drshub;

import bio.terra.drshub.config.DrsHubConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring configuration class for loading application config and code defined beans. */
@Configuration
@EnableConfigurationProperties
public class DrsHubSpringConfig {
  @Bean
  @ConfigurationProperties(value = "drshub", ignoreUnknownFields = false)
  public DrsHubConfig getDrshubConfig() {
    return DrsHubConfig.create();
  }
}
