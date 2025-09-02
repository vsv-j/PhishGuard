package com.ws.phishguard.service;

import com.ws.phishguard.dto.SmsProcessingResponseDto;
import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.OutboxEvent;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.metrics.AppMetrics;
import com.ws.phishguard.repo.OutboxEventRepository;
import com.ws.phishguard.service.handler.ServiceCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmsProcessingServiceImpl implements SmsProcessingService {

  @Value("${phishguard.service.phone-number}")
  private String servicePhoneNumber;

  @Value("${phishguard.kafka.topic.sms-analysis-requests}")
  private String smsAnalysisTopic;

  private final SmsMessageService smsMessageService;
  private final ServiceCommandHandler serviceCommandHandler;
  private final OutboxEventRepository outboxEventRepository;
  private final AppMetrics appMetrics;

  @Override
  @Transactional
  public SmsProcessingResponseDto processSms(SmsRequestDto smsRequest) {
    return appMetrics.SMS_PROCESSING_LATENCY().record(() -> {
      log.debug("Processing SMS from: {} to: {}", smsRequest.sender(), smsRequest.recipient());

      SmsMessage smsMessage = smsMessageService.createOrRetrieveMessage(smsRequest);
      boolean isServiceNumber = servicePhoneNumber.equals(smsRequest.recipient().trim());

      if (isServiceNumber) {
        ProcessingStatus finalStatus = serviceCommandHandler.handle(smsRequest, smsMessage);
        appMetrics.SMS_PROCESSED(finalStatus).increment();
        return new SmsProcessingResponseDto(smsMessage.getId(), finalStatus);
      } else {
        if (smsMessage.getStatus() != MessageStatus.RECEIVED) {
            log.warn("Duplicate request detected. Original messageId: {}", smsMessage.getId());
            ProcessingStatus originalStatus = determineOriginalStatus(smsMessage);
            return new SmsProcessingResponseDto(smsMessage.getId(), originalStatus);
        }
        smsMessageService.updateStatus(smsMessage, MessageStatus.PENDING_ANALYSIS);

        OutboxEvent event = new OutboxEvent(smsAnalysisTopic, smsMessage.getId().toString());
        outboxEventRepository.save(event);
        log.info("Saved outbox event for messageId {} for asynchronous analysis.", smsMessage.getId());

        return new SmsProcessingResponseDto(smsMessage.getId(), ProcessingStatus.ACCEPTED);
      }
    });
  }

  private ProcessingStatus determineOriginalStatus(SmsMessage originalMessage) {
    return switch (originalMessage.getStatus()) {
      case ALLOWED_ANALYZED_SAFE, ALLOWED_NO_URLS, ALLOWED_NOT_SUBSCRIBED -> ProcessingStatus.ALLOWED;
      case REJECTED_PHISHING, REJECTED_ANALYSIS_FAILED -> ProcessingStatus.REJECTED_PHISHING;
      case COMMAND_PROCESSED -> ProcessingStatus.COMMAND_PROCESSED;
      case INVALID_COMMAND -> ProcessingStatus.INVALID_COMMAND;
      default -> ProcessingStatus.ACCEPTED;
    };
  }
}