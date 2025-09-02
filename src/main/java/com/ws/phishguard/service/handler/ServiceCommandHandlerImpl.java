package com.ws.phishguard.service.handler;

import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.service.ProcessingStatus;
import com.ws.phishguard.service.SmsMessageService;
import com.ws.phishguard.service.SubscriberService;
import com.ws.phishguard.service.SubscriptionCommand;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServiceCommandHandlerImpl implements ServiceCommandHandler {

  private final SubscriberService subscriberService;
  private final SmsMessageService smsMessageService;

  @Override
  @Transactional
  public ProcessingStatus handle(SmsRequestDto smsRequest, SmsMessage smsMessage) {
    Optional<SubscriptionCommand> commandOpt = SubscriptionCommand.fromString(smsRequest.message());

    if (commandOpt.isPresent()) {
      log.debug("Processing command '{}' for sender {}", commandOpt.get(), smsRequest.sender());
      subscriberService.manageSubscription(smsRequest.sender(), commandOpt.get());
      smsMessageService.updateStatus(smsMessage, MessageStatus.COMMAND_PROCESSED);
      return ProcessingStatus.COMMAND_PROCESSED;
    } else {
      log.warn(
          "Received invalid command '{}' from sender {} to service number.",
          smsRequest.message().trim(),
          smsRequest.sender());

      smsMessageService.updateStatus(smsMessage, MessageStatus.INVALID_COMMAND);
      return ProcessingStatus.INVALID_COMMAND;
    }
  }
}