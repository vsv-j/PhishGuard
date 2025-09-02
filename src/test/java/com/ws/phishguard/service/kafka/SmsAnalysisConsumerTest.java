package com.ws.phishguard.service.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.exception.WebRiskApiException;
import com.ws.phishguard.metrics.AppMetrics;
import com.ws.phishguard.service.SmsMessageService;
import com.ws.phishguard.service.handler.UserMessageHandler;
import io.micrometer.core.instrument.Counter;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class SmsAnalysisConsumerTest {

  @Mock
  private SmsMessageService smsMessageService;
  @Mock
  private UserMessageHandler userMessageHandler;
  @Mock
  private Acknowledgment ack;
  @Mock
  private AppMetrics appMetrics;
  @Mock
  private Counter mockCounter;

  @InjectMocks
  private SmsAnalysisConsumer smsAnalysisConsumer;

  private SmsMessage smsMessage;
  private UUID messageId;

  @BeforeEach
  void setUp() {
    messageId = UUID.randomUUID();
    smsMessage = new SmsMessage();
    smsMessage.setId(messageId);
    smsMessage.setStatus(MessageStatus.PENDING_ANALYSIS);

    lenient().when(appMetrics.KAFKA_ANALYSIS_CONSUMER_OUTCOME(any(KafkaConsumerOutcome.class)))
        .thenReturn(mockCounter);
  }

  @Nested
  @DisplayName("handleSmsForAnalysis Tests")
  class HandleSmsForAnalysisTests {

    @Test
    @DisplayName("Should process a valid message and update status to ALLOWED_ANALYZED_SAFE")
    void handleSmsForAnalysis_validMessage_updatesToSafe() {
      lenient().when(smsMessageService.findById(messageId)).thenReturn(Optional.of(smsMessage));
      lenient().when(userMessageHandler.handle(smsMessage)).thenReturn(MessageStatus.ALLOWED_ANALYZED_SAFE);

      smsAnalysisConsumer.handleSmsForAnalysis(messageId.toString(), ack);

      verify(smsMessageService).updateStatus(smsMessage, MessageStatus.ALLOWED_ANALYZED_SAFE);
      verify(appMetrics).KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.SUCCESS_ALLOWED_ANALYZED_SAFE);
      verify(mockCounter).increment();
      verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should skip analysis if message is already processed")
    void handleSmsForAnalysis_alreadyProcessed_skipsAndAcks() {
      smsMessage.setStatus(MessageStatus.REJECTED_PHISHING);
      when(smsMessageService.findById(messageId)).thenReturn(Optional.of(smsMessage));

      smsAnalysisConsumer.handleSmsForAnalysis(messageId.toString(), ack);

      verify(userMessageHandler, never()).handle(any());
      verify(smsMessageService, never()).updateStatus(any(), any());
      verify(appMetrics).KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.SKIPPED_ALREADY_PROCESSED);
      verify(mockCounter).increment();
      verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should discard message if it is not found in the database")
    void handleSmsForAnalysis_messageNotFound_discardsAndAcks() {
      when(smsMessageService.findById(messageId)).thenReturn(Optional.empty());

      smsAnalysisConsumer.handleSmsForAnalysis(messageId.toString(), ack);

      verify(userMessageHandler, never()).handle(any());
      verify(appMetrics).KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.MESSAGE_NOT_FOUND);
      verify(mockCounter).increment();
      verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should discard malformed UUID payload without retry")
    void handleSmsForAnalysis_invalidUuid_discardsAndAcks() {
      String invalidPayload = "not-a-uuid";

      smsAnalysisConsumer.handleSmsForAnalysis(invalidPayload, ack);

      verify(smsMessageService, never()).findById(any());
      verify(appMetrics).KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.MALFORMED_PAYLOAD);
      verify(mockCounter).increment();
      verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should update status to FAILED on unexpected handler exception")
    void handleSmsForAnalysis_handlerThrowsUnexpectedException_updatesToFailed() {
      when(smsMessageService.findById(messageId)).thenReturn(Optional.of(smsMessage));
      when(userMessageHandler.handle(smsMessage)).thenThrow(new IllegalStateException("Unexpected error"));

      smsAnalysisConsumer.handleSmsForAnalysis(messageId.toString(), ack);

      verify(smsMessageService).updateStatus(smsMessage, MessageStatus.REJECTED_ANALYSIS_FAILED);
      verify(appMetrics).KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.ERROR_UNEXPECTED);
      verify(mockCounter).increment();
      verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should re-throw WebRiskApiException to trigger retry")
    void handleSmsForAnalysis_handlerThrowsWebRiskApiException_triggersRetry() {
      when(smsMessageService.findById(messageId)).thenReturn(Optional.of(smsMessage));
      WebRiskApiException exception = new WebRiskApiException("API down", null);
      when(userMessageHandler.handle(smsMessage)).thenThrow(exception);

      assertThrows(WebRiskApiException.class, () -> {
        smsAnalysisConsumer.handleSmsForAnalysis(messageId.toString(), ack);
      });

      verify(smsMessageService, never()).updateStatus(any(), any());
      verify(appMetrics, never()).KAFKA_ANALYSIS_CONSUMER_OUTCOME(any());
      verify(ack).acknowledge();
    }
  }

  @Nested
  @DisplayName("handleDlt Tests")
  class DltHandlerTests {

    @Test
    @DisplayName("Should update status to FAILED for a message stuck in PENDING_ANALYSIS")
    void handleDlt_pendingMessage_updatesToFailed() {
      when(smsMessageService.findById(messageId)).thenReturn(Optional.of(smsMessage));

      smsAnalysisConsumer.handleDlt(messageId.toString(), ack);

      verify(smsMessageService).updateStatus(smsMessage, MessageStatus.REJECTED_ANALYSIS_FAILED);
      verify(appMetrics).KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.DLT_RECEIVED);
      verify(mockCounter).increment();
      verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should not update status for a message already in a terminal state")
    void handleDlt_terminalStateMessage_doesNothing() {
      smsMessage.setStatus(MessageStatus.ALLOWED_ANALYZED_SAFE);
      when(smsMessageService.findById(messageId)).thenReturn(Optional.of(smsMessage));

      smsAnalysisConsumer.handleDlt(messageId.toString(), ack);

      verify(smsMessageService, never()).updateStatus(any(), any());
      verify(appMetrics).KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.DLT_RECEIVED);
      verify(mockCounter).increment();
      verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should not fail if DLT message is not found")
    void handleDlt_messageNotFound_doesNotFail() {
      when(smsMessageService.findById(messageId)).thenReturn(Optional.empty());

      smsAnalysisConsumer.handleDlt(messageId.toString(), ack);

      verify(smsMessageService, never()).updateStatus(any(), any());
      verify(appMetrics).KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome.DLT_RECEIVED);
      verify(mockCounter).increment();
      verify(ack).acknowledge();
    }
  }
}