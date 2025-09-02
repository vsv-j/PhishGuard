package com.ws.phishguard.exception;

public class UrlExtractionException extends RuntimeException {
  public UrlExtractionException(String message, Throwable cause) {
    super(message, cause);
  }
}