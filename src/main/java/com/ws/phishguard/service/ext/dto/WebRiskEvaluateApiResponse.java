package com.ws.phishguard.service.ext.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebRiskEvaluateApiResponse(List<Score> scores) {
}