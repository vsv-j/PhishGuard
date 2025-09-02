package com.ws.phishguard.service.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.service.ProcessingStatus;
import com.ws.phishguard.service.SmsMessageService;
import com.ws.phishguard.service.SubscriberService;
import com.ws.phishguard.service.SubscriptionCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceCommandHandlerImplTest {

  @Mock
  private SubscriberService subscriberService;
  @Mock
  private SmsMessageService smsMessageService;

  @InjectMocks
  private ServiceCommandHandlerImpl serviceCommandHandler;

  private static final String SENDER_PHONE = "+1234567890";
  private static final String RECIPIENT_PHONE = "+11112223333"; // Service number

  private SmsRequestDto createSmsRequest(String message) {
    return new SmsRequestDto(SENDER_PHONE, RECIPIENT_PHONE, message);
  }

  private SmsMessage createSmsMessage() {
    SmsMessage smsMessage = new SmsMessage();
    smsMessage.setStatus(MessageStatus.RECEIVED);
    return smsMessage;
  }

  @Nested
  @DisplayName("handle Valid Commands")
  class HandleValidCommands {

    @Test
    @DisplayName("Should process 'START' command and update statuses")
    void handle_validStartCommand_managesSubscriptionAndUpdatesStatus() {
      SmsRequestDto smsRequest = createSmsRequest("START");
      SmsMessage smsMessage = createSmsMessage();

      ProcessingStatus result = serviceCommandHandler.handle(smsRequest, smsMessage);

      verify(subscriberService, times(1)).manageSubscription(SENDER_PHONE, SubscriptionCommand.START);
      verify(smsMessageService, times(1)).updateStatus(smsMessage, MessageStatus.COMMAND_PROCESSED);
      assertEquals(ProcessingStatus.COMMAND_PROCESSED, result);
    }

    @Test
    @DisplayName("Should process 'STOP' command and update statuses")
    void handle_validStopCommand_managesSubscriptionAndUpdatesStatus() {
      SmsRequestDto smsRequest = createSmsRequest("STOP");
      SmsMessage smsMessage = createSmsMessage();

      ProcessingStatus result = serviceCommandHandler.handle(smsRequest, smsMessage);

      verify(subscriberService, times(1)).manageSubscription(SENDER_PHONE, SubscriptionCommand.STOP);
      verify(smsMessageService, times(1)).updateStatus(smsMessage, MessageStatus.COMMAND_PROCESSED);
      assertEquals(ProcessingStatus.COMMAND_PROCESSED, result);
    }
  }

  @Nested
  @DisplayName("handle Invalid Commands")
  class HandleInvalidCommands {

    @Test
    @DisplayName("Should handle invalid command and update status to INVALID_COMMAND")
    void handle_invalidCommand_updatesStatusToInvalidAndReturnsInvalid() {
      SmsRequestDto smsRequest = createSmsRequest("INVALID_CMD");
      SmsMessage smsMessage = createSmsMessage();

      ProcessingStatus result = serviceCommandHandler.handle(smsRequest, smsMessage);

      verify(subscriberService, never()).manageSubscription(any(), any());
      verify(smsMessageService, times(1)).updateStatus(smsMessage, MessageStatus.INVALID_COMMAND);
      assertEquals(ProcessingStatus.INVALID_COMMAND, result);
    }

    @Test
    @DisplayName("Should handle empty command and update status to INVALID_COMMAND")
    void handle_emptyCommand_updatesStatusToInvalidAndReturnsInvalid() {
      SmsRequestDto smsRequest = createSmsRequest("   ");
      SmsMessage smsMessage = createSmsMessage();

      ProcessingStatus result = serviceCommandHandler.handle(smsRequest, smsMessage);

      verify(subscriberService, never()).manageSubscription(any(), any());
      verify(smsMessageService, times(1)).updateStatus(smsMessage, MessageStatus.INVALID_COMMAND);
      assertEquals(ProcessingStatus.INVALID_COMMAND, result);
    }
  }
}