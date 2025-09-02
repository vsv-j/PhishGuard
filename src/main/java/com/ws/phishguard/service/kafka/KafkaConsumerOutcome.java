package com.ws.phishguard.service.kafka;

import com.ws.phishguard.entity.MessageStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KafkaConsumerOutcome {
  MALFORMED_PAYLOAD("malformed_payload"),
  MESSAGE_NOT_FOUND("message_not_found"),
  SKIPPED_ALREADY_PROCESSED("skipped_already_processed"),
  ERROR_UNEXPECTED("error_unexpected"),
  DLT_RECEIVED("dlt_received"),

  SUCCESS_ALLOWED_ANALYZED_SAFE("success_allowed_analyzed_safe"),
  SUCCESS_ALLOWED_NO_URLS("success_allowed_no_urls"),
  SUCCESS_ALLOWED_NOT_SUBSCRIBED("success_allowed_not_subscribed"),
  SUCCESS_REJECTED_PHISHING("success_rejected_phishing");

  private final String tagName;

  public static KafkaConsumerOutcome fromSuccessStatus(MessageStatus status) {
    return switch (status) {
      case ALLOWED_ANALYZED_SAFE -> SUCCESS_ALLOWED_ANALYZED_SAFE;
      case ALLOWED_NO_URLS -> SUCCESS_ALLOWED_NO_URLS;
      case ALLOWED_NOT_SUBSCRIBED -> SUCCESS_ALLOWED_NOT_SUBSCRIBED;
      case REJECTED_PHISHING -> SUCCESS_REJECTED_PHISHING;
      default -> throw new IllegalArgumentException(
          "Cannot map MessageStatus '" + status + "' to a success metric outcome.");
    };
  }
}
