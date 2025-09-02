package com.ws.phishguard.service.ext.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a single threat score from an external analysis service, detailing the
 * type of threat and the confidence in that assessment. This is a part of a
 * larger response from a service like Google Web Risk.
 *
 * @param threatType      The category of threat identified (e.g., "PHISHING", "MALWARE").
 * @param confidenceLevel The level of confidence the service has in this threat assessment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Score(String threatType, ConfidenceLevel confidenceLevel) {
}