package bio.terra.drshub.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.TokenAuthenticatedRequestFactory;
import bio.terra.drshub.generated.api.DrsHubApi;
import bio.terra.drshub.generated.model.RequestObject;
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
  private final TokenAuthenticatedRequestFactory tokenAuthenticatedRequestFactory;

  public DrsHubApiController(
      HttpServletRequest request,
      MetadataService metadataService,
      TokenAuthenticatedRequestFactory tokenAuthenticatedRequestFactory) {
    this.request = request;
    this.metadataService = metadataService;
    this.tokenAuthenticatedRequestFactory = tokenAuthenticatedRequestFactory;
  }

  @Override
  public ResponseEntity<ResourceMetadata> resolveDrs(RequestObject body) {
    var tokenAuthenticatedRequest = tokenAuthenticatedRequestFactory.from(request);
    validateRequest(body);

    var userAgent = request.getHeader("user-agent");
    var forceAccessUrl = Objects.equals(request.getHeader("drshub-force-access-url"), "true");
    var ip = request.getHeader("X-Forwarded-For");

    log.info("Received URL '{}' from agent '{}' on IP '{}'", body.getUrl(), userAgent, ip);

    var resourceMetadata =
        metadataService.fetchResourceMetadata(
            body.getUrl(), body.getFields(), tokenAuthenticatedRequest, forceAccessUrl);

    return ResponseEntity.ok(resourceMetadata);
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
