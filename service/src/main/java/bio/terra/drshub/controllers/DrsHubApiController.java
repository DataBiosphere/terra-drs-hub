package bio.terra.drshub.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.drshub.generated.api.DrsHubApi;
import bio.terra.drshub.generated.model.RequestObject;
import bio.terra.drshub.generated.model.ResolvedMetadata;
import bio.terra.drshub.generated.model.ResourceMetadata;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.MetadataService;
import java.util.ArrayList;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class DrsHubApiController implements DrsHubApi {

  private final HttpServletRequest request;
  private final MetadataService metadataService;

  public DrsHubApiController(HttpServletRequest request, MetadataService metadataService) {
    this.request = request;
    this.metadataService = metadataService;
  }

  @Override
  public ResponseEntity<ResourceMetadata> getFile(RequestObject body) {
    var auth = request.getHeader("authorization");

    validateRequest(body, auth);

    var userAgent = request.getHeader("user-agent");
    var forceAccessUrl = Objects.equals(request.getHeader("drshub-force-access-url"), "true");
    var ip = request.getHeader("X-Forwarded-For");

    log.info("Received URL '{}' from agent '{}' on IP '{}'", body.getUrl(), userAgent, ip);

    var resourceMetadata =
        metadataService.fetchResourceMetadata(
            body.getUrl(), body.getFields(), auth.substring(7), forceAccessUrl);

    return ResponseEntity.ok(resourceMetadata);
  }

  @Override
  public ResponseEntity<ResolvedMetadata> getMetadata(String uri, Boolean signedUrl) {
    var auth = request.getHeader("authorization");

    if (auth == null) {
      throw new UnauthorizedException("Authorization header is missing.");
    }
    var userAgent = request.getHeader("user-agent");
    var ip = request.getHeader("X-Forwarded-For");

    log.info("Received URL '{}' from agent '{}' on IP '{}'", uri, userAgent, ip);

    var resourceMetadata = metadataService.fetchResourceMetadata(uri, auth.substring(7), signedUrl);

    return ResponseEntity.ok(resourceMetadata);
  }

  private void validateRequest(RequestObject body, String auth) {
    if (auth == null) {
      throw new UnauthorizedException("Authorization header is missing.");
    }
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
