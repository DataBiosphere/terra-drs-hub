package bio.terra.drshub.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.generated.api.DrsHubApi;
import bio.terra.drshub.generated.model.RequestObject;
import bio.terra.drshub.generated.model.ResourceMetadata;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.DrsResolutionService;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class DrsHubApiController implements DrsHubApi {

  private final HttpServletRequest request;
  private final DrsResolutionService drsResolutionService;
  private final BearerTokenFactory bearerTokenFactory;
  private final DrsHubConfig drsHubConfig;

  public DrsHubApiController(
      HttpServletRequest request,
      DrsResolutionService drsResolutionService,
      BearerTokenFactory bearerTokenFactory,
      DrsHubConfig drsHubConfig) {
    this.request = request;
    this.drsResolutionService = drsResolutionService;
    this.bearerTokenFactory = bearerTokenFactory;
    this.drsHubConfig = drsHubConfig;
  }

  @Override
  public ResponseEntity<ResourceMetadata> resolveDrs(RequestObject body) {
    var bearerToken = bearerTokenFactory.from(request);
    validateRequest(body);

    var userAgent = request.getHeader("user-agent");
    var forceAccessUrl = Objects.equals(request.getHeader("drshub-force-access-url"), "true");
    var ip = request.getHeader("X-Forwarded-For");

    log.info("Received URL {} from agent {} on IP {}", body.getUrl(), userAgent, ip);

    try {
      var resourceMetadata =
          drsResolutionService
              .resolveDrsObject(body.getUrl(), body.getFields(), bearerToken, forceAccessUrl, ip)
              .get(drsHubConfig.getPencilsDownSeconds(), TimeUnit.SECONDS);

      return ResponseEntity.ok(resourceMetadata);
    } catch (InterruptedException | TimeoutException | ExecutionException ex) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
  }

  private void validateRequest(RequestObject body) {
    var errors = new ArrayList<String>();

    if (body == null || body.getUrl() == null) {
      errors.add("Missing url in request body");
    }

    if (body != null
        && body.getFields() != null
        && !Fields.ALL_FIELDS.containsAll(body.getFields())) {
      errors.add(
          String.format("Some fields were not valid. Supported fields are %s", Fields.ALL_FIELDS));
    }

    if (!errors.isEmpty()) {
      throw new BadRequestException(String.join(",", errors));
    }
  }
}
