package bio.terra.drshub.util;

import bio.terra.drshub.generated.model.ServiceName;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public class RequestUtils {

  public static Optional<ServiceName> serviceNameFromRequest(HttpServletRequest request) {
    return Optional.ofNullable(request.getHeader("x-terra-service-id"))
        .map(String::toLowerCase)
        .map(ServiceName::fromValue);
  }
}
