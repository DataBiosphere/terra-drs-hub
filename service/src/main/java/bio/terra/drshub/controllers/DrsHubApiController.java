package bio.terra.drshub.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.drshub.generated.api.DrsHubApi;
import bio.terra.drshub.generated.model.GetSignedUrlRequest;
import bio.terra.drshub.generated.model.RequestObject;
import bio.terra.drshub.generated.model.ResourceMetadata;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.DrsResolutionService;
import bio.terra.drshub.services.SignedUrlService;
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
  private final DrsResolutionService drsResolutionService;
  private final BearerTokenFactory bearerTokenFactory;
  private final SignedUrlService signedUrlService;

  public DrsHubApiController(
      HttpServletRequest request,
      DrsResolutionService drsResolutionService,
      BearerTokenFactory bearerTokenFactory,
      SignedUrlService signedUrlService) {
    this.request = request;
    this.drsResolutionService = drsResolutionService;
    this.bearerTokenFactory = bearerTokenFactory;
    this.signedUrlService = signedUrlService;
  }

  @Override
  public ResponseEntity<ResourceMetadata> resolveDrs(RequestObject body) {
    var bearerToken = bearerTokenFactory.from(request);
    validateRequest(body);

    var userAgent = request.getHeader("user-agent");
    var forceAccessUrl = Objects.equals(request.getHeader("drshub-force-access-url"), "true");
    var ip = request.getHeader("X-Forwarded-For");

    log.info("Received URL {} from agent {} on IP {}", body.getUrl(), userAgent, ip);

    var resourceMetadata =
        drsResolutionService.resolveDrsObject(
            body.getUrl(), body.getFields(), bearerToken, forceAccessUrl, ip);

    return ResponseEntity.ok(resourceMetadata);
  }

  @Override
  public ResponseEntity<String> getSignedUrl(GetSignedUrlRequest body) {
    var bearerToken = bearerTokenFactory.from(request);
    var ip = request.getHeader("X-Forwarded-For");
    var signedUrl =
        signedUrlService.getSignedUrl(
            body.getBucket(),
            body.getObject(),
            body.getDataObjectUri(),
            body.getGoogleProject(),
            bearerToken,
            ip);
    return ResponseEntity.ok(signedUrl.toString());
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
