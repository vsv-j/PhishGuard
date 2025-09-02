package com.ws.phishguard.service.kafka;

import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.exception.WebRiskApiException;
import com.ws.phishguard.metrics.AppMetrics;
import com.ws.phishguard.service.SmsMessageService;
import com.ws.phishguard.service.handler.UserMessageHandler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmsAnalysisConsumer {

  private final SmsMessageService smsMessageService;
  private final UserMessageHandler userMessageHandler;
  private final AppMetrics appMetrics;

  @RetryableTopic(
      attempts = "${phishguard.kafka.retry.attempts:4}",
      backoff = @Backoff(
          delayExpression = "${phishguard.kafka.retry.delay:60000}",
          multiplierExpression = "${phishguard.kafka.retry.multiplier:5.0}"
      ),
      autoCreateTopics = "true",
      include = {WebRiskApiException.class},
      dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR
  )
  @KafkaListener(
      topics = "${phishguard.kafka.topic.sms-analysis-requests}",
      groupId = "phishguard-analysis-consumer",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void handleSmsForAnalysis(@Payload String payload, Acknowledgment ack) {
    UUID messageId;
    try {
      messageId = UUID.fromString(payload);
    } catch (IllegalArgumentException e) {
      log.error("Received malformed message payload which is not a valid UUID: '{}'. Discarding message without retry.", payload);
      appMetrics.KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.MALFORMED_PAYLOAD).increment();
      ack.acknowledge();
      return;
    }

    log.debug("Received message for analysis with ID: {}", messageId);

    SmsMessage smsMessage = smsMessageService.findById(messageId)
        .orElse(null);

    if (smsMessage == null) {
      log.warn("Received analysis request for a non-existent messageId: {}. Discarding.", messageId);
      appMetrics.KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.MESSAGE_NOT_FOUND).increment();
      ack.acknowledge();
      return;
    }

    if (smsMessage.getStatus() != MessageStatus.PENDING_ANALYSIS) {
      log.warn("Message {} already processed with status: {}. Skipping analysis.", messageId, smsMessage.getStatus());
      appMetrics.KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.SKIPPED_ALREADY_PROCESSED).increment();
      ack.acknowledge();
      return;
    }

    try {
      MessageStatus finalStatus = userMessageHandler.handle(smsMessage);
      smsMessageService.updateStatus(smsMessage, finalStatus);
      log.debug("Successfully processed message {}. Final status: {}", messageId, finalStatus);
      appMetrics.KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.fromSuccessStatus(finalStatus)).increment();
    } catch (WebRiskApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error during analysis of message {}. Updating status to FAILED.", messageId, e);
      appMetrics.KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.ERROR_UNEXPECTED).increment();
      smsMessageService.updateStatus(smsMessage, MessageStatus.REJECTED_ANALYSIS_FAILED);
    } finally {
      ack.acknowledge();
    }
  }

  @DltHandler
  public void handleDlt(String payload, Acknowledgment ack) {
    log.error("FATAL: Message has failed all processing attempts and is now in the DLT. Payload: '{}'", payload);
    appMetrics.KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.DLT_RECEIVED).increment();
    try {
      UUID messageId = UUID.fromString(payload);
      smsMessageService.findById(messageId).ifPresent(message -> {
        if (message.getStatus() == MessageStatus.PENDING_ANALYSIS) {
          log.warn("Pessimistically blocking message {} due to repeated analysis failures.", messageId);
          smsMessageService.updateStatus(message, MessageStatus.REJECTED_ANALYSIS_FAILED);
        } else {
          log.warn("DLT message {} was already in a terminal state: {}. No action taken.", messageId, message.getStatus());
        }
      });
    } catch (Exception e) {
      log.error("CRITICAL: Failed to apply pessimistic block to DLT message with payload: {}", payload, e);
    } finally {
      ack.acknowledge();
    }
  }
}