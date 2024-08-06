package bio.terra.drshub.tracking;

import bio.terra.bard.model.EventProperties;
import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.services.TrackingService;
import bio.terra.drshub.util.AsyncUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Class to intercept requests and log them to the tracking service (Bard). Methods annotated with
 * the {@link TrackCall} annotation will be tracked.
 */
@Component
@Slf4j
public record TrackingInterceptor(
    TrackingService trackingService,
    AsyncUtils asyncUtils,
    BearerTokenFactory bearerTokenFactory,
    ObjectMapper objectMapper,
    DrsHubConfig config)
    implements HandlerInterceptor {

  public static final String EVENT_NAME = "drshub:api";

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull Object handler,
      Exception ex) {
    var path = request.getRequestURI();
    var responseStatus = HttpStatus.valueOf(response.getStatus());
    // Note: invalid responses will not be logged since there are no sessions to associate users
    // with
    if (config.bardEventLoggingEnabled()
        && handler instanceof HandlerMethod handlerMethod
        && handlerMethod.getMethod().getAnnotation(TrackCall.class) != null
        && responseStatus.is2xxSuccessful()) {
      var bearerToken = bearerTokenFactory.from(request);
      var properties =
          new HashMap<String, Object>(
              Map.of("statusCode", responseStatus.value(), "requestUrl", path));

      properties.putAll(readRequestBody(request));

      // There are all the known headers that are potentially sent to DRSHub that we want to track
      addToPropertiesIfPresentInHeader(request, properties, "x-user-project", "userProject");
      addToPropertiesIfPresentInHeader(
          request, properties, "drshub-force-access-url", "forceAccessUrl");
      addToPropertiesIfPresentInHeader(request, properties, "x-app-id", "serviceName");

      addResolvedCloudToProperties(response, properties);
      addTDRSnapshotIdToProperties(properties);

      trackingService.logEvent(bearerToken, EVENT_NAME, properties);
    }
  }

  private Map<String, Object> readRequestBody(HttpServletRequest request) {
    var rawRequest =
        new String(
            ((ContentCachingRequestWrapper) request).getContentAsByteArray(),
            StandardCharsets.UTF_8);
    return readBody(rawRequest);
  }

  private Map<String, Object> readResponseBody(HttpServletResponse response) {
    var rawResponse =
        new String(
            ((ContentCachingResponseWrapper) response).getContentAsByteArray(),
            StandardCharsets.UTF_8);
    return readBody(rawResponse);
  }

  private Map<String, Object> readBody(String body) {
    try {
      return objectMapper.readValue(body, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      // Note: log a warning but do not fail so that at least part of the request is logged
      log.warn("Failed to parse request body for tracking", e);
      return Map.of();
    }
  }

  /**
   * If a given header is present in the request and has a value, add it to the properties map to be
   * tracked in Bard
   *
   * @param request The request being logged
   * @param properties The properties map that will be sent to Bard for tracking as an {@link
   *     EventProperties} object
   * @param headerName The name of the header to examine
   * @param propertyName The name of the map key to use when adding the header value to the
   *     properties map
   */
  private void addToPropertiesIfPresentInHeader(
      HttpServletRequest request,
      Map<String, Object> properties,
      String headerName,
      String propertyName) {
    var headerValue = request.getHeader(headerName);
    if (headerValue != null) {
      properties.put(propertyName, headerValue);
    }
  }

  public void addResolvedCloudToProperties(
      HttpServletResponse response, Map<String, Object> properties) {
    Map<String, Object> responseBody = readResponseBody(response);
    if (responseBody.get("accessUrl") != null) {
      Map<String, String> accessURLResponse = (Map<String, String>) responseBody.get("accessUrl");
      String url = accessURLResponse.get("url");
      String resolvedCloud = "";
      if (url.contains("windows.net")) {
        resolvedCloud = "azure";
      } else if (url.contains("googleapis.com")) {
        resolvedCloud = "gcp";
      }
      properties.put("resolvedCloud", resolvedCloud);
    }
  }

  public void addTDRSnapshotIdToProperties(Map<String, Object> properties) {
    String drsURI = properties.get("url").toString();
    if (drsURI != null) {
      var tdrProvider = config.getDrsProviders().get("terraDataRepo");
      if (drsURI.matches(tdrProvider.getHostRegex() + ".*")) {
        var drsURIParts = drsURI.split("/");
        var objectId = drsURIParts[drsURIParts.length - 1];
        // The TDR drs object id has the format v1_snapshotId_objectId
        var snapshotId = objectId.split("_")[1];
        properties.put("snapshotId", snapshotId);
      }
    }
  }
}
