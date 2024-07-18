package bio.terra.drshub.util;

import bio.terra.drshub.generated.model.ServiceName;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public class RequestUtils {

  public static Optional<ServiceName> serviceNameFromRequest(HttpServletRequest request) {
    var header = Optional.ofNullable(request.getHeader("x-app-id"));
    var result =
        header
            .map(String::toLowerCase)
            .map(RequestUtils::serviceNameMapper)
            .map(ServiceName::fromValue);
    if (header.isPresent() && result.isEmpty()) {
      throw new IllegalArgumentException("Invalid service name: " + header.get());
    }
    return result;
  }

  private static String serviceNameMapper(String name) {
    return switch (name) {
      case "saturn" -> "terra_ui";
      default -> name;
    };
  }
}
