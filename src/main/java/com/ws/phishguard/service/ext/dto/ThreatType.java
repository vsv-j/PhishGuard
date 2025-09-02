package com.ws.phishguard.service.ext.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the threat types that can be sent to the Google Web Risk API.
 * The names must match the strings expected by the API.
 */
public enum ThreatType {
  SOCIAL_ENGINEERING,
  MALWARE,
  UNWANTED_SOFTWARE;

  /**
   * This annotation tells Jackson to serialize the enum to this string value.
   * In this case, it's the uppercase name, which is what the API expects.
   */
  @JsonValue
  public String getApiValue() {
    return this.name();
  }
}