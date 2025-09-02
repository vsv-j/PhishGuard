package com.ws.phishguard.entity;

/**
 * Represents the detailed processing status of an SMS message for auditing.
 */
public enum MessageStatus {
  RECEIVED,
  PENDING_ANALYSIS,

  COMMAND_PROCESSED,
  INVALID_COMMAND,

  ALLOWED_NOT_SUBSCRIBED,
  ALLOWED_NO_URLS,
  ALLOWED_ANALYZED_SAFE,

  REJECTED_PHISHING,
  REJECTED_ANALYSIS_FAILED
}