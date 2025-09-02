package com.ws.phishguard.service.ext.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Represents the threat details if a URL is found to be malicious.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreatInfo(List<String> threatTypes) {
}