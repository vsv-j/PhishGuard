package com.ws.phishguard.service.handler;

import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.service.ProcessingStatus;

public interface ServiceCommandHandler {

  ProcessingStatus handle(SmsRequestDto smsRequest, SmsMessage smsMessage);
}