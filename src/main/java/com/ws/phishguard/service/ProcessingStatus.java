package com.ws.phishguard.service;

/**
 * Represents the outcome of the SMS processing logic.
 */
public enum ProcessingStatus {

  ACCEPTED,
  COMMAND_PROCESSED,
  INVALID_COMMAND,
  ALLOWED,
  REJECTED_PHISHING
}