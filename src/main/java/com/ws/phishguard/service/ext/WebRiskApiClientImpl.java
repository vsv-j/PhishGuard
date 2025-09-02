package com.ws.phishguard.service.ext;

import com.ws.phishguard.exception.WebRiskApiException;
import com.ws.phishguard.metrics.AppMetrics;
import com.ws.phishguard.service.ext.dto.ThreatType;
import com.ws.phishguard.service.ext.dto.WebRiskApiRequest;
import com.ws.phishguard.service.ext.dto.WebRiskEvaluateApiResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
@Primary
public class WebRiskApiClientImpl implements WebRiskApiClient {

  private final RestClient restClient;
  private final AppMetrics appMetrics;
  private final String apiKey;

  private static final List<ThreatType> THREAT_TYPES_TO_EVALUATE = List.of(
      ThreatType.SOCIAL_ENGINEERING,
      ThreatType.MALWARE,
      ThreatType.UNWANTED_SOFTWARE
  );

  public WebRiskApiClientImpl(
      @Qualifier("webRiskRestClient") RestClient restClient,
      @Value("${google.webrisk.api-key}") String apiKey,
      AppMetrics appMetrics
  ) {
    this.restClient = restClient;
    this.apiKey = apiKey;
    this.appMetrics = appMetrics;
  }

  @Override
  @CircuitBreaker(name = "webRiskApi", fallbackMethod = "recoverFromWebRiskApiFailure")
  public Optional<WebRiskEvaluateApiResponse> fetchThreatEvaluation(String url) {
    WebRiskApiRequest requestBody = new WebRiskApiRequest(url, THREAT_TYPES_TO_EVALUATE);
    log.debug("Querying Web Risk API for URL: {}", url);

    try {
      WebRiskEvaluateApiResponse response = appMetrics.WEB_RISK_API_LATENCY().recordCallable(() ->
          restClient.post()
              .uri(uriBuilder -> uriBuilder
                  .path(":evaluateUri")
                  .queryParam("key", apiKey)
                  .build())
              .body(requestBody)
              .retrieve()
              .body(WebRiskEvaluateApiResponse.class)
      );

      log.debug("Web Risk API response for URL [{}]: {}", url, response);
      return Optional.ofNullable(response);
    } catch (HttpServerErrorException | ResourceAccessException e) {
      throw new WebRiskApiException("Transient error calling Web Risk API for URL: " + url, e);
    } catch (Exception e) {
      log.error("Permanent client error during Web Risk API call. URL : {}", url, e);
      throw new RuntimeException("Permanent client error during Web Risk API call", e);
    }
  }

  public Optional<WebRiskEvaluateApiResponse> recoverFromWebRiskApiFailure(String url, Throwable e) {
    log.error("Circuit breaker fallback for Web Risk API call to URL [{}]. Reason: {} - {}",
        url, e.getClass().getSimpleName(), e.getMessage());
    appMetrics.WEB_RISK_API_FAILURES(e.getClass()).increment();
    if (e instanceof WebRiskApiException) {
      throw (WebRiskApiException) e;
    } else {
      throw new RuntimeException("Permanent failure in Web Risk API, fallback triggered for URL: " + url, e);
    }
  }
}