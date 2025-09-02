package com.ws.phishguard.service.ext.dto;

import java.util.List;

/**
 * Represents the JSON body for the Web Risk evaluateUri POST request.
 * Uses the ThreatType enum for type safety.
 */
public record WebRiskApiRequest(String uri, List<ThreatType> threatTypes) {
}