package com.ws.phishguard.service.ext.dto;

/**
 * Represents the outcome of a URL threat analysis from the Web Risk API client.
 */
public enum UrlAnalysisStatus {
  /**
   * The API was reached and confirmed the URL is a threat or not.
   */
  THREAT_FOUND,
  SAFE,

  /**
   * The API was reached, but it had no information about the URL.
   * The result is inconclusive and should not be cached.
   */
  INCONCLUSIVE,

  /**
   * The API call failed due to a transient error (e.g., network issue, 5xx).
   * The result is unknown.
   */
  API_FAILURE
}