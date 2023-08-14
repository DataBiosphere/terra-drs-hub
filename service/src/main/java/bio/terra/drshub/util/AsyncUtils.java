package bio.terra.drshub.util;

import bio.terra.common.exception.ServiceUnavailableException;
import bio.terra.drshub.DrsHubException;
import bio.terra.drshub.config.DrsHubConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public record AsyncUtils(DrsHubConfig drsHubConfig) {

  public <T, U> U runAndCatch(CompletableFuture<T> completableFuture, Function<T, U> mapper) {
    try {
      T result = completableFuture.get(drsHubConfig.getPencilsDownSeconds(), TimeUnit.SECONDS);
      return mapper.apply(result);
    } catch (TimeoutException ex) {
      throw new ServiceUnavailableException(ex);
    } catch (InterruptedException ex) {
      log.error("Encountered an InterruptedException while executing an async task", ex);
      Thread.currentThread().interrupt();
      throw new DrsHubException(ex);
    } catch (ExecutionException ex) {
      var cause = ex.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      } else {
        throw new DrsHubException(ex);
      }
    }
  }
}
