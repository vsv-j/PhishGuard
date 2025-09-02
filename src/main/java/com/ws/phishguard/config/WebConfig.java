package com.ws.phishguard.config;

import com.ws.phishguard.security.ApiKeyAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(apiKeyAuthInterceptor)
        .addPathPatterns("/api/v1/sms/incoming", "/api/v1/sms/{messageId}");
  }
}