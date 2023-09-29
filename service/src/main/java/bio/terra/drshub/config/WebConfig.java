package bio.terra.drshub.config;

import bio.terra.drshub.logging.LoggerInterceptor;
import bio.terra.drshub.tracking.TrackingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class WebConfig implements WebMvcConfigurer {
  @Autowired private LoggerInterceptor loggerInterceptor;
  @Autowired private TrackingInterceptor trackingInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(loggerInterceptor);
    registry.addInterceptor(trackingInterceptor);
  }
}
