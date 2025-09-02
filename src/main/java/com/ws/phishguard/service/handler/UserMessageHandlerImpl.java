package com.ws.phishguard.service.handler;

import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.entity.Subscriber;
import com.ws.phishguard.service.SubscriberService;
import com.ws.phishguard.service.UrlAnalysisCacheService;
import com.ws.phishguard.util.HashGenerator;
import com.ws.phishguard.util.UrlExtractor;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserMessageHandlerImpl implements UserMessageHandler {

  private final SubscriberService subscriberService;
  private final UrlAnalysisCacheService urlAnalysisCacheService;
  private final HashGenerator hashGenerator;
  private final UrlExtractor urlExtractor;

  @Override
  public MessageStatus handle(SmsMessage smsMessage) {
    String recipientHash = hashGenerator.hash(smsMessage.getRecipient());
    Optional<Subscriber> subscriberOpt = subscriberService.findByPhoneNumberHash(recipientHash);

    if (subscriberOpt.isEmpty() || !subscriberOpt.get().isActive()) {
      log.debug(
          "Recipient for message [{}] is not an active subscriber. Allowing message without analysis.",
          smsMessage.getId());
      return MessageStatus.ALLOWED_NOT_SUBSCRIBED;
    }

    List<String> urls = urlExtractor.extractUrls(smsMessage.getMessageContent());

    if (urls.isEmpty()) {
      return MessageStatus.ALLOWED_NO_URLS;
    }

    if (urlAnalysisCacheService.containsPhishingUrl(urls)) {
      log.debug("Phishing URL detected in message [{}]. Rejecting message.", smsMessage.getId());
      return MessageStatus.REJECTED_PHISHING;
    }

    return MessageStatus.ALLOWED_ANALYZED_SAFE;
  }
}