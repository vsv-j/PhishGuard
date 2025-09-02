package com.ws.phishguard.service;

import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.mapper.SmsMessageMapper;
import com.ws.phishguard.repo.SmsMessageRepository;
import com.ws.phishguard.util.HashGenerator;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SmsMessageServiceImpl implements SmsMessageService {

  private final SmsMessageRepository smsMessageRepository;
  private final SmsMessageMapper smsMessageMapper;
  private final HashGenerator hashGenerator;

  @Override
  @Transactional
  public SmsMessage createOrRetrieveMessage(SmsRequestDto smsRequest) {
    String idempotencyKeySource = smsRequest.sender() + smsRequest.recipient() + smsRequest.message();
    String idempotencyKey = hashGenerator.hash(idempotencyKeySource);

    Optional<SmsMessage> existingMessage = smsMessageRepository.findByIdempotencyKey(idempotencyKey);
    if (existingMessage.isPresent()) {
      return existingMessage.get();
    }

    SmsMessage smsMessage = smsMessageMapper.toEntity(smsRequest);
    smsMessage.setIdempotencyKey(idempotencyKey);
    smsMessage.setStatus(MessageStatus.RECEIVED);
    return smsMessageRepository.save(smsMessage);
  }

  @Override
  @Transactional
  public void updateStatus(SmsMessage message, MessageStatus status) {
    message.setStatus(status);
    smsMessageRepository.save(message);
  }

  @Override
  public Optional<SmsMessage> findById(UUID id) {
    return smsMessageRepository.findById(id);
  }
}