package com.ws.phishguard.exception;

public class WebRiskApiException extends RuntimeException {
  public WebRiskApiException(String message, Throwable cause) {
    super(message, cause);
  }
}