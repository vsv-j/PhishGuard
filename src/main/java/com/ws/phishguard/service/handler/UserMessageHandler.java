package com.ws.phishguard.service.handler;

import com.ws.phishguard.entity.MessageStatus; // Changed import
import com.ws.phishguard.entity.SmsMessage;

public interface UserMessageHandler {

  MessageStatus handle(SmsMessage smsMessage);
}