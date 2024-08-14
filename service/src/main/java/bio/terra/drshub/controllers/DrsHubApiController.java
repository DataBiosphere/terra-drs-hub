package bio.terra.drshub.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.drshub.generated.api.DrsHubApi;
import bio.terra.drshub.generated.model.RequestObject;
import bio.terra.drshub.generated.model.ResourceMetadata;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.services.DrsResolutionService;
import bio.terra.drshub.tracking.TrackCall;
import bio.terra.drshub.tracking.UserLoggingMetrics;
import bio.terra.drshub.util.AsyncUtils;
import bio.terra.drshub.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public record DrsHubApiController(
    HttpServletRequest request,
    DrsResolutionService drsResolutionService,
    BearerTokenFactory bearerTokenFactory,
    AsyncUtils asyncUtils,
    UserLoggingMetrics userLoggingMetrics)
    implements DrsHubApi {

  @Override
  @TrackCall
  public ResponseEntity<ResourceMetadata> resolveDrs(RequestObject body) {
    var bearerToken = bearerTokenFactory.from(request);
    validateRequest(body);

    var userAgent = request.getHeader("user-agent");
    var forceAccessUrl = Objects.equals(request.getHeader("drshub-force-access-url"), "true");
    var ip = request.getHeader("X-Forwarded-For");
    var googleProject = request.getHeader("x-user-project");
    var serviceName = RequestUtils.serviceNameFromRequest(request);

    log.info("Received URL {} from agent {} on IP {}", body.getUrl(), userAgent, ip);
    String transactionId = drsResolutionService.getTransactionId();
    userLoggingMetrics.set("transactionId", transactionId);

    return asyncUtils.runAndCatch(
        drsResolutionService.resolveDrsObject(
            body.getUrl(),
            body.getCloudPlatform(),
            body.getFields(),
            serviceName,
            bearerToken,
            forceAccessUrl,
            ip,
            googleProject,
            transactionId),
        ResponseEntity::ok);
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
