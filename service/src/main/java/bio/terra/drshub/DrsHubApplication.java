package bio.terra.drshub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {
      "bio.terra.drshub",
      "bio.terra.common.logging",
      "bio.terra.common.tracing",
      "bio.terra.common.iam"
    },
    excludeFilters = @Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class))
public class DrsHubApplication {

  public static void main(String[] args) {
    SpringApplication.run(DrsHubApplication.class, args);
  }
}
