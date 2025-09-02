package com.ws.phishguard.service;

import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.SmsMessage;
import java.util.Optional;
import java.util.UUID;

public interface SmsMessageService {

  SmsMessage createOrRetrieveMessage(SmsRequestDto smsRequest);

  void updateStatus(SmsMessage message, MessageStatus status);

  Optional<SmsMessage> findById(UUID id);
}