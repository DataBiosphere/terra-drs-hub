package bio.terra.drshub.controllers;

import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.VersionProperties;
import bio.terra.drshub.generated.api.PublicApi;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class PublicApiController implements PublicApi {

  private final DrsHubConfig drsHubConfig;

  public PublicApiController(DrsHubConfig drsHubConfig) {
    this.drsHubConfig = drsHubConfig;
  }

  @Override
  public ResponseEntity<Void> getStatus() {
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<VersionProperties> getVersion() {
    return Optional.ofNullable(drsHubConfig.getVersion())
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
