package bio.terra.drshub.util;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.generated.model.ServiceName;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@Tag("Unit")
class RequestUtilsTest extends BaseTest {

  @Test
  void testServiceNameFromRequest() {
    // Arrange
    var request = new MockHttpServletRequest();
    request.addHeader("x-terra-service-id", "terra_ui");

    // Act
    var result = RequestUtils.serviceNameFromRequest(request);

    // Assert
    assertEquals(Optional.of(ServiceName.TERRA_UI), result);
  }

  @Test
  void testServiceNameFromRequestWithCapitalizations() {
    // Arrange
    var request = new MockHttpServletRequest();
    request.addHeader("X-Terra-Service-ID", "Terra_UI");

    // Act
    var result = RequestUtils.serviceNameFromRequest(request);

    // Assert
    assertEquals(Optional.of(ServiceName.TERRA_UI), result);
  }

  @Test
  void testServiceNameFromRequestNoHeader() {
    // Arrange
    var request = new MockHttpServletRequest();

    // Act
    var result = RequestUtils.serviceNameFromRequest(request);

    // Assert
    assertTrue(result.isEmpty());
  }

  @Test
  void testServiceNameFromRequestInvalidHeader() {
    // Arrange
    var request = new MockHttpServletRequest();
    request.addHeader("x-terra-service-id", "invalid");

    // Act
    var result = RequestUtils.serviceNameFromRequest(request);

    // Assert
    assertTrue(result.isEmpty());
  }
}
