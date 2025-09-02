package com.ws.phishguard.service;

import static com.ws.phishguard.service.ext.dto.UrlAnalysisStatus.INCONCLUSIVE;
import static com.ws.phishguard.service.ext.dto.UrlAnalysisStatus.SAFE;
import static com.ws.phishguard.service.ext.dto.UrlAnalysisStatus.THREAT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ws.phishguard.service.ext.WebRiskApiClient;
import com.ws.phishguard.service.ext.dto.ConfidenceLevel;
import com.ws.phishguard.service.ext.dto.Score;
import com.ws.phishguard.service.ext.dto.UrlAnalysisStatus;
import com.ws.phishguard.service.ext.dto.WebRiskEvaluateApiResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UrlThreatDecisionServiceImplTest {

  @Mock
  private WebRiskApiClient webRiskApiClient;

  @InjectMocks
  private UrlThreatDecisionServiceImpl urlThreatDecisionService;

  private static final String TEST_URL = "http://example.com";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(urlThreatDecisionService, "threatThreshold", ConfidenceLevel.MEDIUM);
  }

  @Nested
  @DisplayName("determineUrlStatus with API Responses")
  class DetermineUrlStatusApiResponses {

    @Test
    @DisplayName("Should return INCONCLUSIVE if WebRiskApiClient returns empty Optional")
    void determineUrlStatus_emptyApiResponse_returnsInconclusive() {
      when(webRiskApiClient.fetchThreatEvaluation(anyString())).thenReturn(Optional.empty());

      UrlAnalysisStatus status = urlThreatDecisionService.determineUrlStatus(TEST_URL);

      assertEquals(INCONCLUSIVE, status);
    }

    @Test
    @DisplayName("Should return INCONCLUSIVE if API response has null scores")
    void determineUrlStatus_responseWithNullScores_returnsInconclusive() {
      WebRiskEvaluateApiResponse apiResponse = new WebRiskEvaluateApiResponse(null);
      when(webRiskApiClient.fetchThreatEvaluation(anyString())).thenReturn(Optional.of(apiResponse));

      UrlAnalysisStatus status = urlThreatDecisionService.determineUrlStatus(TEST_URL);

      assertEquals(INCONCLUSIVE, status);
    }

    @Test
    @DisplayName("Should return INCONCLUSIVE if API response has empty scores")
    void determineUrlStatus_responseWithEmptyScores_returnsInconclusive() {
      WebRiskEvaluateApiResponse apiResponse = new WebRiskEvaluateApiResponse(Collections.emptyList());
      when(webRiskApiClient.fetchThreatEvaluation(anyString())).thenReturn(Optional.of(apiResponse));

      UrlAnalysisStatus status = urlThreatDecisionService.determineUrlStatus(TEST_URL);

      assertEquals(INCONCLUSIVE, status);
    }
  }

  @Nested
  @DisplayName("determineUrlStatus with Threat Threshold Logic")
  class DetermineUrlStatusThreatLogic {

    @Test
    @DisplayName("Should return THREAT_FOUND if any score is at or above threshold")
    void determineUrlStatus_threatFoundAboveThreshold_returnsThreatFound() {
      ReflectionTestUtils.setField(urlThreatDecisionService, "threatThreshold", ConfidenceLevel.MEDIUM);
      List<Score> scores = List.of(
          new Score("MALWARE", ConfidenceLevel.LOW),
          new Score("PHISHING", ConfidenceLevel.HIGH), // This one is above MEDIUM
          new Score("UNWANTED_SOFTWARE", ConfidenceLevel.SAFE)
      );
      WebRiskEvaluateApiResponse apiResponse = new WebRiskEvaluateApiResponse(scores);
      when(webRiskApiClient.fetchThreatEvaluation(anyString())).thenReturn(Optional.of(apiResponse));

      UrlAnalysisStatus status = urlThreatDecisionService.determineUrlStatus(TEST_URL);

      assertEquals(THREAT_FOUND, status);
    }

    @Test
    @DisplayName("Should return SAFE if all scores are below threshold")
    void determineUrlStatus_noThreatAboveThreshold_returnsSafe() {
      ReflectionTestUtils.setField(urlThreatDecisionService, "threatThreshold", ConfidenceLevel.MEDIUM);
      List<Score> scores = List.of(
          new Score("MALWARE", ConfidenceLevel.LOW),
          new Score("PHISHING", ConfidenceLevel.SAFE),
          new Score("UNWANTED_SOFTWARE", ConfidenceLevel.LOW)
      );
      WebRiskEvaluateApiResponse apiResponse = new WebRiskEvaluateApiResponse(scores);
      when(webRiskApiClient.fetchThreatEvaluation(anyString())).thenReturn(Optional.of(apiResponse));

      UrlAnalysisStatus status = urlThreatDecisionService.determineUrlStatus(TEST_URL);

      assertEquals(SAFE, status);
    }

    @Test
    @DisplayName("Should return SAFE if a score matches threshold but is not higher")
    void determineUrlStatus_threatAtThreshold_returnsThreatFound() {
      ReflectionTestUtils.setField(urlThreatDecisionService, "threatThreshold", ConfidenceLevel.MEDIUM);
      List<Score> scores = List.of(
          new Score("MALWARE", ConfidenceLevel.LOW),
          new Score("PHISHING", ConfidenceLevel.MEDIUM),
          new Score("UNWANTED_SOFTWARE", ConfidenceLevel.SAFE)
      );
      WebRiskEvaluateApiResponse apiResponse = new WebRiskEvaluateApiResponse(scores);
      when(webRiskApiClient.fetchThreatEvaluation(anyString())).thenReturn(Optional.of(apiResponse));

      UrlAnalysisStatus status = urlThreatDecisionService.determineUrlStatus(TEST_URL);

      assertEquals(THREAT_FOUND, status);
    }

    @Test
    @DisplayName("Should filter out scores with null confidence levels")
    void determineUrlStatus_scoresWithNullConfidenceLevel_filtersOut() {
      ReflectionTestUtils.setField(urlThreatDecisionService, "threatThreshold", ConfidenceLevel.MEDIUM);
      List<Score> scores = List.of(
          new Score("MALWARE", null),
          new Score("PHISHING", ConfidenceLevel.LOW)
      );
      WebRiskEvaluateApiResponse apiResponse = new WebRiskEvaluateApiResponse(scores);
      when(webRiskApiClient.fetchThreatEvaluation(anyString())).thenReturn(Optional.of(apiResponse));

      UrlAnalysisStatus status = urlThreatDecisionService.determineUrlStatus(TEST_URL);

      assertEquals(SAFE, status);
    }

    @Test
    @DisplayName("Should return THREAT_FOUND even if some scores are null, if others are above threshold")
    void determineUrlStatus_mixedScoresWithNullAndThreat_returnsThreatFound() {
      ReflectionTestUtils.setField(urlThreatDecisionService, "threatThreshold", ConfidenceLevel.MEDIUM);
      List<Score> scores = List.of(
          new Score("MALWARE", null),
          new Score("PHISHING", ConfidenceLevel.HIGH),
          new Score("UNWANTED_SOFTWARE", ConfidenceLevel.LOW)
      );
      WebRiskEvaluateApiResponse apiResponse = new WebRiskEvaluateApiResponse(scores);
      when(webRiskApiClient.fetchThreatEvaluation(anyString())).thenReturn(Optional.of(apiResponse));

      UrlAnalysisStatus status = urlThreatDecisionService.determineUrlStatus(TEST_URL);

      assertEquals(THREAT_FOUND, status);
    }
  }
}