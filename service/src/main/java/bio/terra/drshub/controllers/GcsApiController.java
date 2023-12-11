package bio.terra.drshub.controllers;

import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.drshub.generated.api.GcsApi;
import bio.terra.drshub.generated.model.GetSignedUrlRequest;
import bio.terra.drshub.services.SignedUrlService;
import bio.terra.drshub.tracking.TrackCall;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class GcsApiController implements GcsApi {

  private final HttpServletRequest request;
  private final BearerTokenFactory bearerTokenFactory;
  private final SignedUrlService signedUrlService;

  public GcsApiController(
      HttpServletRequest request,
      BearerTokenFactory bearerTokenFactory,
      SignedUrlService signedUrlService) {
    this.request = request;
    this.bearerTokenFactory = bearerTokenFactory;
    this.signedUrlService = signedUrlService;
  }

  @Override
  @TrackCall
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
}
