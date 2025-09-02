package com.ws.phishguard.service;

import static com.ws.phishguard.service.ext.dto.UrlAnalysisStatus.SAFE;
import static com.ws.phishguard.service.ext.dto.UrlAnalysisStatus.THREAT_FOUND;

import com.ws.phishguard.service.ext.WebRiskApiClient;
import com.ws.phishguard.service.ext.dto.ConfidenceLevel;
import com.ws.phishguard.service.ext.dto.UrlAnalysisStatus;
import com.ws.phishguard.service.ext.dto.WebRiskEvaluateApiResponse;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UrlThreatDecisionServiceImpl implements UrlThreatDecisionService {

  private final WebRiskApiClient webRiskApiClient;

  @Value("${google.webrisk.threat-threshold-level}")
  private ConfidenceLevel threatThreshold;

  @Override
  public UrlAnalysisStatus determineUrlStatus(String url) {
    Optional<WebRiskEvaluateApiResponse> apiResponseOpt = webRiskApiClient.fetchThreatEvaluation(url);

    if (apiResponseOpt.isEmpty()) {
      log.warn(
          "Web Risk API returned a successful but empty response for URL [{}]. Treating as INCONCLUSIVE.",
          url);
      return UrlAnalysisStatus.INCONCLUSIVE;
    }

    WebRiskEvaluateApiResponse response = apiResponseOpt.get();

    if (response.scores() == null || response.scores().isEmpty()) {
      return UrlAnalysisStatus.INCONCLUSIVE;
    }

    boolean isThreat = response.scores().stream()
        .filter(score -> Objects.nonNull(score.confidenceLevel()))
        .anyMatch(score -> score.confidenceLevel().ordinal() >= threatThreshold.ordinal());

    return isThreat ? THREAT_FOUND : SAFE;
  }
}
