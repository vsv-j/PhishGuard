package com.ws.phishguard.service.ext.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the top-level response from the Google Web Risk evaluateUri API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebRiskApiResponse(ThreatInfo threat) {
}