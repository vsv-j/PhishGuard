package com.ws.phishguard.service.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ws.phishguard.exception.WebRiskApiException;
import com.ws.phishguard.metrics.AppMetrics;
import com.ws.phishguard.service.ext.dto.Score;
import com.ws.phishguard.service.ext.dto.WebRiskApiRequest;
import com.ws.phishguard.service.ext.dto.WebRiskEvaluateApiResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@ExtendWith(MockitoExtension.class)
class WebRiskApiClientImplTest {

  @Mock
  private RestClient restClient;
  @Mock
  private AppMetrics appMetrics;

  // Mocks for the fluent RestClient chain
  @Mock
  private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  @Mock
  private RestClient.RequestBodySpec requestBodySpec;
  @Mock
  private RestClient.ResponseSpec responseSpec;
  @Mock
  private Counter mockCounter;

  @InjectMocks
  private WebRiskApiClientImpl webRiskApiClient;

  @Captor
  private ArgumentCaptor<WebRiskApiRequest> requestBodyCaptor;
  @Captor
  private ArgumentCaptor<Function<UriBuilder, java.net.URI>> uriFunctionCaptor;

  private static final String API_KEY = "test-api-key";
  private static final String TEST_URL = "http://test-url.com";

  @BeforeEach
  void setUp() {
    Timer realTimer = new SimpleMeterRegistry().timer("test.timer");
    lenient().when(appMetrics.WEB_RISK_API_LATENCY()).thenReturn(realTimer);

    org.springframework.test.util.ReflectionTestUtils.setField(webRiskApiClient, "apiKey", API_KEY);

    lenient().when(restClient.post()).thenReturn(requestBodyUriSpec);
    lenient().when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
    lenient().when(requestBodySpec.body(any(WebRiskApiRequest.class))).thenReturn(requestBodySpec);
    lenient().when(requestBodySpec.retrieve()).thenReturn(responseSpec);
  }

  @Nested
  @DisplayName("fetchThreatEvaluation Success Path")
  class SuccessPath {
    @Test
    @DisplayName("Should return response when API call is successful")
    void fetchThreatEvaluation_success() {
      WebRiskEvaluateApiResponse expectedResponse = new WebRiskEvaluateApiResponse(List.of(new Score("PHISHING", null)));
      when(responseSpec.body(WebRiskEvaluateApiResponse.class)).thenReturn(expectedResponse);

      Optional<WebRiskEvaluateApiResponse> actualResponse = webRiskApiClient.fetchThreatEvaluation(TEST_URL);

      assertTrue(actualResponse.isPresent());
      assertEquals(expectedResponse, actualResponse.get());

      verify(requestBodySpec).body(requestBodyCaptor.capture());
      assertEquals(TEST_URL, requestBodyCaptor.getValue().uri());
    }

    @Test
    @DisplayName("Should return empty optional when API returns a null body")
    void fetchThreatEvaluation_nullBody() {
      when(responseSpec.body(WebRiskEvaluateApiResponse.class)).thenReturn(null);

      Optional<WebRiskEvaluateApiResponse> actualResponse = webRiskApiClient.fetchThreatEvaluation(TEST_URL);

      assertTrue(actualResponse.isEmpty());
    }
  }

  @Nested
  @DisplayName("fetchThreatEvaluation Exception Handling")
  class ExceptionHandling {
    @Test
    @DisplayName("Should throw WebRiskApiException for HttpServerErrorException")
    void fetchThreatEvaluation_throwsWebRiskApiException_onHttpServerError() {
      when(responseSpec.body(WebRiskEvaluateApiResponse.class)).thenThrow(HttpServerErrorException.class);

      assertThrows(WebRiskApiException.class, () -> webRiskApiClient.fetchThreatEvaluation(TEST_URL));
    }

    @Test
    @DisplayName("Should throw WebRiskApiException for ResourceAccessException")
    void fetchThreatEvaluation_throwsWebRiskApiException_onResourceAccessError() {
      when(responseSpec.body(WebRiskEvaluateApiResponse.class)).thenThrow(ResourceAccessException.class);

      assertThrows(WebRiskApiException.class, () -> webRiskApiClient.fetchThreatEvaluation(TEST_URL));
    }

    @Test
    @DisplayName("Should throw RuntimeException for other client errors")
    void fetchThreatEvaluation_throwsRuntimeException_onOtherErrors() {
      when(responseSpec.body(WebRiskEvaluateApiResponse.class)).thenThrow(HttpClientErrorException.class);

      assertThrows(RuntimeException.class, () -> webRiskApiClient.fetchThreatEvaluation(TEST_URL));
    }
  }

  @Nested
  @DisplayName("recoverFromWebRiskApiFailure Fallback Logic")
  class FallbackLogic {
    @Test
    @DisplayName("Should re-throw WebRiskApiException if that was the cause")
    void recoverFromWebRiskApiFailure_rethrowsWebRiskApiException() {
      WebRiskApiException originalException = new WebRiskApiException("Transient error", null);
      when(appMetrics.WEB_RISK_API_FAILURES(WebRiskApiException.class)).thenReturn(mockCounter);

      WebRiskApiException thrown = assertThrows(WebRiskApiException.class,
          () -> webRiskApiClient.recoverFromWebRiskApiFailure(TEST_URL, originalException));

      assertEquals(originalException, thrown);
      verify(mockCounter).increment();
    }

    @Test
    @DisplayName("Should throw RuntimeException for other exceptions")
    void recoverFromWebRiskApiFailure_throwsRuntimeExceptionForOtherCauses() {
      IllegalStateException originalException = new IllegalStateException("Some other error");
      when(appMetrics.WEB_RISK_API_FAILURES(IllegalStateException.class)).thenReturn(mockCounter);

      RuntimeException thrown = assertThrows(RuntimeException.class,
          () -> webRiskApiClient.recoverFromWebRiskApiFailure(TEST_URL, originalException));

      assertEquals("Permanent failure in Web Risk API, fallback triggered for URL: " + TEST_URL, thrown.getMessage());
      assertEquals(originalException, thrown.getCause());
      verify(mockCounter).increment();
    }
  }
}