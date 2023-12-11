package bio.terra.drshub;

import bio.terra.common.exception.ErrorReportException;
import jakarta.annotation.Nullable;
import java.util.List;
import org.springframework.http.HttpStatus;

public class DrsHubException extends ErrorReportException {

  public DrsHubException(String message) {
    super(message);
  }

  public DrsHubException(String message, Throwable cause) {
    super(message, cause);
  }

  public DrsHubException(Throwable cause) {
    super(cause);
  }

  public DrsHubException(Throwable cause, HttpStatus statusCode) {
    super(cause, statusCode);
  }

  public DrsHubException(
      String message, @Nullable List<String> causes, @Nullable HttpStatus statusCode) {
    super(message, causes, statusCode);
  }

  public DrsHubException(
      String message,
      Throwable cause,
      @Nullable List<String> causes,
      @Nullable HttpStatus statusCode) {
    super(message, cause, causes, statusCode);
  }
}
