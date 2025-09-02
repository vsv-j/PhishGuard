package com.ws.phishguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.mapper.SmsMessageMapper;
import com.ws.phishguard.repo.SmsMessageRepository;
import com.ws.phishguard.util.HashGenerator;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
class SmsMessageServiceImplTest {

  @Mock
  private SmsMessageRepository smsMessageRepository;
  @Mock
  private SmsMessageMapper smsMessageMapper;
  @Mock
  private HashGenerator hashGenerator;

  @InjectMocks
  private SmsMessageServiceImpl smsMessageService;

  @Captor
  private ArgumentCaptor<SmsMessage> smsMessageCaptor;

  private static final String IDEMPOTENCY_KEY = "mock-idempotency-key";
  private SmsRequestDto smsRequest;

  @BeforeEach
  void setUp() {
    smsRequest = new SmsRequestDto("+1sender", "+1recipient", "test message");
    lenient().when(hashGenerator.hash(anyString())).thenReturn(IDEMPOTENCY_KEY);
  }

  @Nested
  @DisplayName("createOrRetrieveMessage Tests")
  class CreateOrRetrieveMessageTests {

    @Test
    @DisplayName("Should create and save a new message if it does not exist")
    void createOrRetrieveMessage_whenMessageIsNew_createsAndSaves() {
      SmsMessage newMessage = new SmsMessage();
      when(smsMessageRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
      when(smsMessageMapper.toEntity(smsRequest)).thenReturn(newMessage);
      when(smsMessageRepository.save(any(SmsMessage.class))).thenReturn(newMessage);

      SmsMessage result = smsMessageService.createOrRetrieveMessage(smsRequest);

      verify(smsMessageRepository).findByIdempotencyKey(IDEMPOTENCY_KEY);
      verify(smsMessageMapper).toEntity(smsRequest);
      verify(smsMessageRepository).save(smsMessageCaptor.capture());

      SmsMessage savedMessage = smsMessageCaptor.getValue();
      assertEquals(IDEMPOTENCY_KEY, savedMessage.getIdempotencyKey());
      assertEquals(MessageStatus.RECEIVED, savedMessage.getStatus());
      assertEquals(newMessage, result);
    }

    @Test
    @DisplayName("Should retrieve an existing message if idempotency key matches")
    void createOrRetrieveMessage_whenMessageExists_retrievesAndReturns() {
      SmsMessage existingMessage = new SmsMessage();
      existingMessage.setId(UUID.randomUUID());
      when(smsMessageRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
          .thenReturn(Optional.of(existingMessage));

      SmsMessage result = smsMessageService.createOrRetrieveMessage(smsRequest);

      verify(smsMessageRepository).findByIdempotencyKey(IDEMPOTENCY_KEY);
      verify(smsMessageMapper, never()).toEntity(any());
      verify(smsMessageRepository, never()).save(any());
      assertEquals(existingMessage, result);
    }
  }

  @Nested
  @DisplayName("updateStatus Tests")
  class UpdateStatusTests {

    @Test
    @DisplayName("Should set the new status and save the message")
    void updateStatus_setsStatusAndSaves() {
      SmsMessage message = new SmsMessage();
      message.setStatus(MessageStatus.RECEIVED);
      MessageStatus newStatus = MessageStatus.PENDING_ANALYSIS;

      smsMessageService.updateStatus(message, newStatus);

      verify(smsMessageRepository, times(1)).save(smsMessageCaptor.capture());
      SmsMessage savedMessage = smsMessageCaptor.getValue();
      assertEquals(newStatus, savedMessage.getStatus());
    }
  }

  @Nested
  @DisplayName("findById Tests")
  class FindByIdTests {

    @Test
    @DisplayName("Should delegate the call to the repository")
    void findById_delegatesToRepository() {
      UUID messageId = UUID.randomUUID();
      SmsMessage message = new SmsMessage();
      message.setId(messageId);
      when(smsMessageRepository.findById(messageId)).thenReturn(Optional.of(message));

      Optional<SmsMessage> result = smsMessageService.findById(messageId);

      verify(smsMessageRepository).findById(messageId);
      assertTrue(result.isPresent());
      assertEquals(message, result.get());
    }
  }
}