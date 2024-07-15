package bio.terra.drshub.util;

import bio.terra.drshub.generated.model.ServiceName;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public class RequestUtils {

  public static Optional<ServiceName> serviceNameFromRequest(HttpServletRequest request) {
    var header = Optional.ofNullable(request.getHeader("x-terra-service-id"));
    var result = header.map(String::toLowerCase).map(ServiceName::fromValue);
    if (header.isPresent() && result.isEmpty()) {
      throw new IllegalArgumentException("Invalid service name: " + header.get());
    }
    return result;
  }
}
