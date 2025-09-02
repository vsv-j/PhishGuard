package com.ws.phishguard.service.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ws.phishguard.entity.MessageStatus;
import com.ws.phishguard.entity.SmsMessage;
import com.ws.phishguard.entity.Subscriber;
import com.ws.phishguard.service.SubscriberService;
import com.ws.phishguard.service.UrlAnalysisCacheService;
import com.ws.phishguard.util.HashGenerator;
import com.ws.phishguard.util.UrlExtractor;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserMessageHandlerImplTest {

  @Mock
  private SubscriberService subscriberService;
  @Mock
  private UrlAnalysisCacheService urlAnalysisCacheService;
  @Mock
  private HashGenerator hashGenerator;
  @Mock
  private UrlExtractor urlExtractor;

  @InjectMocks
  private UserMessageHandlerImpl userMessageHandler;

  private static final String RECIPIENT_PHONE = "+15551234567";
  private static final String RECIPIENT_HASH = "hashed-recipient";
  private SmsMessage smsMessage;

  @BeforeEach
  void setUp() {
    smsMessage = new SmsMessage();
    smsMessage.setRecipient(RECIPIENT_PHONE);
    smsMessage.setMessageContent("Some message content");

    when(hashGenerator.hash(RECIPIENT_PHONE)).thenReturn(RECIPIENT_HASH);
  }

  @Nested
  @DisplayName("Subscriber Checks")
  class SubscriberChecks {

    @Test
    @DisplayName("Should allow message if recipient is not a subscriber")
    void handle_recipientNotSubscriber_returnsAllowedNotSubscribed() {
      when(subscriberService.findByPhoneNumberHash(RECIPIENT_HASH)).thenReturn(Optional.empty());

      MessageStatus result = userMessageHandler.handle(smsMessage);

      assertEquals(MessageStatus.ALLOWED_NOT_SUBSCRIBED, result);
      verify(urlExtractor, never()).extractUrls(smsMessage.getMessageContent());
      verify(urlAnalysisCacheService, never()).containsPhishingUrl(Collections.emptyList());
    }

    @Test
    @DisplayName("Should allow message if recipient is an inactive subscriber")
    void handle_recipientIsInactive_returnsAllowedNotSubscribed() {
      Subscriber inactiveSubscriber = new Subscriber();
      inactiveSubscriber.setActive(false);
      when(subscriberService.findByPhoneNumberHash(RECIPIENT_HASH))
          .thenReturn(Optional.of(inactiveSubscriber));

      MessageStatus result = userMessageHandler.handle(smsMessage);

      assertEquals(MessageStatus.ALLOWED_NOT_SUBSCRIBED, result);
      verify(urlExtractor, never()).extractUrls(smsMessage.getMessageContent());
    }
  }

  @Nested
  @DisplayName("URL and Analysis Checks for Active Subscribers")
  class AnalysisChecks {

    @BeforeEach
    void setUpActiveSubscriber() {
      Subscriber activeSubscriber = new Subscriber();
      activeSubscriber.setActive(true);
      when(subscriberService.findByPhoneNumberHash(RECIPIENT_HASH))
          .thenReturn(Optional.of(activeSubscriber));
    }

    @Test
    @DisplayName("Should allow message if it contains no URLs")
    void handle_activeSubscriberAndNoUrls_returnsAllowedNoUrls() {
      when(urlExtractor.extractUrls(smsMessage.getMessageContent())).thenReturn(Collections.emptyList());

      MessageStatus result = userMessageHandler.handle(smsMessage);

      assertEquals(MessageStatus.ALLOWED_NO_URLS, result);
      verify(urlAnalysisCacheService, never()).containsPhishingUrl(Collections.emptyList());
    }

    @Test
    @DisplayName("Should reject message if it contains a known phishing URL")
    void handle_activeSubscriberAndPhishingUrl_returnsRejectedPhishing() {
      List<String> urls = List.of("http://phishing.com");
      when(urlExtractor.extractUrls(smsMessage.getMessageContent())).thenReturn(urls);
      when(urlAnalysisCacheService.containsPhishingUrl(urls)).thenReturn(true);

      MessageStatus result = userMessageHandler.handle(smsMessage);

      assertEquals(MessageStatus.REJECTED_PHISHING, result);
    }

    @Test
    @DisplayName("Should allow message if it contains only safe URLs")
    void handle_activeSubscriberAndSafeUrls_returnsAllowedAnalyzedSafe() {
      List<String> urls = List.of("http://safe.com");
      when(urlExtractor.extractUrls(smsMessage.getMessageContent())).thenReturn(urls);
      when(urlAnalysisCacheService.containsPhishingUrl(urls)).thenReturn(false);

      MessageStatus result = userMessageHandler.handle(smsMessage);

      assertEquals(MessageStatus.ALLOWED_ANALYZED_SAFE, result);
    }
  }
}