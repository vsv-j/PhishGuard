package com.ws.phishguard.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

@Component
@Slf4j
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

  @Value("${phishguard.security.api-key}")
  private String requiredApiKey;

  private static final String API_KEY_HEADER = "X-API-Key";

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String providedKey = request.getHeader(API_KEY_HEADER);

    if (providedKey == null || !Objects.equals(providedKey, requiredApiKey)) {
      log.warn("Unauthorized request to {}: Invalid or missing API Key.", request.getRequestURI());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
      return false;
    }

    log.debug("API Key validated successfully for request to {}", request.getRequestURI());
    return true;
  }
}