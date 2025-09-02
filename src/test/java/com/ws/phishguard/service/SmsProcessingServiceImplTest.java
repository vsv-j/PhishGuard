package com.ws.phishguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ws.phishguard.dto.SmsProcessingResponseDto;
import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.OutboxEvent;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.metrics.AppMetrics;
import com.ws.phishguard.repo.OutboxEventRepository;
import com.ws.phishguard.service.handler.ServiceCommandHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SmsProcessingServiceImplTest {

  private static final String SERVICE_PHONE_NUMBER = "+15005550006";
  private static final String USER_PHONE_NUMBER = "+15005550007";
  private static final String SMS_ANALYSIS_TOPIC = "sms-analysis-requests";

  @Mock
  private SmsMessageService smsMessageService;
  @Mock
  private ServiceCommandHandler serviceCommandHandler;
  @Mock
  private OutboxEventRepository outboxEventRepository;
  @Mock
  private AppMetrics appMetrics;
  @Mock
  private Counter mockCounter;

  @InjectMocks
  private SmsProcessingServiceImpl smsProcessingService;

  @Captor
  private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

  private final Timer realTimer = new SimpleMeterRegistry().timer("test.timer");

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(smsProcessingService, "servicePhoneNumber", SERVICE_PHONE_NUMBER);
    ReflectionTestUtils.setField(smsProcessingService, "smsAnalysisTopic", SMS_ANALYSIS_TOPIC);
    when(appMetrics.SMS_PROCESSING_LATENCY()).thenReturn(realTimer);
  }

  private SmsRequestDto createSmsRequest(String recipient, String message) {
    return new SmsRequestDto(USER_PHONE_NUMBER, recipient, message);
  }

  @Nested
  @DisplayName("Standard SMS Processing")
  class StandardSmsProcessing {

    @Test
    @DisplayName("Should accept SMS for analysis and create an outbox event")
    void processSms_forAnalysis_createsOutboxEventAndReturnsAccepted() {
      SmsRequestDto smsRequest = createSmsRequest(USER_PHONE_NUMBER, "Check http://some.url");
      SmsMessage newMessage = new SmsMessage();
      newMessage.setId(UUID.randomUUID());
      newMessage.setStatus(MessageStatus.RECEIVED);

      when(smsMessageService.createOrRetrieveMessage(smsRequest)).thenReturn(newMessage);

      SmsProcessingResponseDto response = smsProcessingService.processSms(smsRequest);

      assertEquals(ProcessingStatus.ACCEPTED, response.status());
      assertEquals(newMessage.getId(), response.messageId());

      verify(smsMessageService).updateStatus(newMessage, MessageStatus.PENDING_ANALYSIS);
      verify(outboxEventRepository).save(outboxEventCaptor.capture());

      OutboxEvent savedEvent = outboxEventCaptor.getValue();
      assertEquals(SMS_ANALYSIS_TOPIC, savedEvent.getTopic());
      assertEquals(newMessage.getId().toString(), savedEvent.getPayload());

      verify(serviceCommandHandler, never()).handle(any(), any());
    }
  }

  @Nested
  @DisplayName("Service Command Processing")
  class ServiceCommandProcessing {

    @Test
    @DisplayName("Should delegate to command handler for SMS sent to the service number")
    void processSms_forServiceNumber_delegatesToCommandHandler() {
      SmsRequestDto smsRequest = createSmsRequest(SERVICE_PHONE_NUMBER, "START");
      SmsMessage newMessage = new SmsMessage();
      newMessage.setId(UUID.randomUUID());
      newMessage.setStatus(MessageStatus.RECEIVED);

      when(smsMessageService.createOrRetrieveMessage(smsRequest)).thenReturn(newMessage);
      when(serviceCommandHandler.handle(smsRequest, newMessage)).thenReturn(ProcessingStatus.COMMAND_PROCESSED);
      when(appMetrics.SMS_PROCESSED(ProcessingStatus.COMMAND_PROCESSED)).thenReturn(mockCounter);

      SmsProcessingResponseDto response = smsProcessingService.processSms(smsRequest);

      assertEquals(ProcessingStatus.COMMAND_PROCESSED, response.status());
      assertEquals(newMessage.getId(), response.messageId());

      verify(serviceCommandHandler).handle(smsRequest, newMessage);
      verify(mockCounter).increment();
      verify(outboxEventRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("Duplicate Request Handling")
  class DuplicateRequestHandling {

    @Test
    @DisplayName("Should return original status for a duplicate request")
    void processSms_forDuplicateRequest_returnsOriginalStatus() {
      SmsRequestDto smsRequest = createSmsRequest(USER_PHONE_NUMBER, "Check http://some.url");
      SmsMessage existingMessage = new SmsMessage();
      existingMessage.setId(UUID.randomUUID());
      existingMessage.setStatus(MessageStatus.REJECTED_PHISHING); // Not RECEIVED

      when(smsMessageService.createOrRetrieveMessage(smsRequest)).thenReturn(existingMessage);

      SmsProcessingResponseDto response = smsProcessingService.processSms(smsRequest);

      assertEquals(ProcessingStatus.REJECTED_PHISHING, response.status());
      assertEquals(existingMessage.getId(), response.messageId());

      verify(smsMessageService, never()).updateStatus(any(), any());
      verify(outboxEventRepository, never()).save(any());
      verify(serviceCommandHandler, never()).handle(any(), any());
    }
  }
}