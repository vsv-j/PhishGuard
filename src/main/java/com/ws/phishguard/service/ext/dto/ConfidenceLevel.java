package com.ws.phishguard.service.ext.dto;

/**
 * Represents the confidence level of a threat assessment, from an external
 * service Google Web Risk API. It indicates how certain the service is that a
 * resource (e.g., a URL) is malicious.
 */
public enum ConfidenceLevel {
  CONFIDENCE_LEVEL_UNSPECIFIED,
  SAFE,
  LOW,
  MEDIUM,
  HIGH,
  HIGHER,
  VERY_HIGH,
  EXTREMELY_HIGH;
}