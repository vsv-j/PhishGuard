package com.ws.phishguard.service.ext;

import com.ws.phishguard.service.ext.dto.WebRiskEvaluateApiResponse;
import java.util.Optional;

public interface WebRiskApiClient {

  Optional<WebRiskEvaluateApiResponse> fetchThreatEvaluation(String url);
}