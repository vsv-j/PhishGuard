package com.ws.phishguard.service;

import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.dto.SmsProcessingResponseDto;

public interface SmsProcessingService {

  /**
   * Processes an incoming SMS message.
   * <p>
   * If the message is a service command (e.g., START/STOP), it is handled synchronously.
   * <p>
   * If the message is a standard user message, it is queued for asynchronous analysis.
   * The method will return immediately with an {@link ProcessingStatus#ACCEPTED} status.
   *
   * @param smsRequest The incoming SMS data.
   * @return A DTO containing the result of the processing, including the message ID and status.
   */
  SmsProcessingResponseDto processSms(SmsRequestDto smsRequest);
}