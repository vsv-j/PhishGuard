package com.ws.phishguard.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AppConfig {

  @Bean
  @Qualifier("webRiskRestClient")
  public RestClient webRiskRestClient(
      @Value("${google.webrisk.base-url}") String baseUrl,
      @Value("${google.webrisk.connect-timeout-seconds:1}") int connectTimeoutSeconds,
      @Value("${google.webrisk.read-timeout-seconds:2}") int readTimeoutSeconds) {

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
    factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .build();
  }
}