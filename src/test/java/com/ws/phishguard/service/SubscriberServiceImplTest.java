package com.ws.phishguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ws.phishguard.entity.Subscriber;
import com.ws.phishguard.repo.SubscriberRepository;
import com.ws.phishguard.util.HashGenerator;
import java.util.Optional;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class SubscriberServiceImplTest {

  @Mock
  private SubscriberRepository subscriberRepository;

  @Mock
  private HashGenerator hashGenerator;

  @InjectMocks
  private SubscriberServiceImpl subscriberService;

  @Captor
  private ArgumentCaptor<Subscriber> subscriberCaptor;

  private static final String PHONE_NUMBER = "1234567890";
  private static final String PHONE_NUMBER_HASH = "hashed-1234567890";

  @BeforeEach
  void setUp() {
    lenient().when(hashGenerator.hash(PHONE_NUMBER)).thenReturn(PHONE_NUMBER_HASH);
  }

  @Nested
  @DisplayName("findByPhoneNumberHash")
  class FindByHashTests {
    @Test
    @DisplayName("Should delegate to repository and return its result")
    void findByPhoneNumberHash_shouldDelegateToRepository() {
      Subscriber subscriber = new Subscriber();
      subscriber.setPhoneNumberHash(PHONE_NUMBER_HASH);
      when(subscriberRepository.findByPhoneNumberHash(PHONE_NUMBER_HASH)).thenReturn(Optional.of(subscriber));

      Optional<Subscriber> result = subscriberService.findByPhoneNumberHash(PHONE_NUMBER_HASH);

      assertTrue(result.isPresent());
      assertEquals(subscriber, result.get());
      verify(subscriberRepository).findByPhoneNumberHash(PHONE_NUMBER_HASH);
    }
  }

  @Nested
  @DisplayName("manageSubscription for a New Subscriber")
  class NewSubscriberTests {

    @Test
    @DisplayName("Should create and activate a new subscriber in a single save operation on START command")
    void manageSubscription_startForNewSubscriber_createsAndActivatesInOneSave() {
      when(subscriberRepository.findByPhoneNumberHash(PHONE_NUMBER_HASH)).thenReturn(Optional.empty());
      when(subscriberRepository.save(any(Subscriber.class))).thenAnswer(invocation -> invocation.getArgument(0));

      subscriberService.manageSubscription(PHONE_NUMBER, SubscriptionCommand.START);

      verify(subscriberRepository, times(1)).save(subscriberCaptor.capture());
      Subscriber savedSubscriber = subscriberCaptor.getValue();

      assertEquals(PHONE_NUMBER, savedSubscriber.getPhoneNumber());
      assertEquals(PHONE_NUMBER_HASH, savedSubscriber.getPhoneNumberHash());
      assertTrue(savedSubscriber.isActive(), "A new subscriber on START should be saved as active.");
    }
  }

  @Nested
  @DisplayName("manageSubscription for an Existing Subscriber")
  class ExistingSubscriberTests {

    @Test
    @DisplayName("Should activate an existing inactive subscriber on START command")
    void manageSubscription_startForInactiveSubscriber_activates() {
      Subscriber existingSubscriber = new Subscriber();
      existingSubscriber.setActive(false);
      when(subscriberRepository.findByPhoneNumberHash(PHONE_NUMBER_HASH)).thenReturn(Optional.of(existingSubscriber));
      when(subscriberRepository.save(any(Subscriber.class))).thenReturn(existingSubscriber);

      subscriberService.manageSubscription(PHONE_NUMBER, SubscriptionCommand.START);

      verify(subscriberRepository, times(1)).save(subscriberCaptor.capture());
      Subscriber savedSubscriber = subscriberCaptor.getValue();

      assertTrue(savedSubscriber.isActive());
      assertEquals(existingSubscriber, savedSubscriber);
    }

    @Test
    @DisplayName("Should deactivate an existing active subscriber on STOP command")
    void manageSubscription_stopForActiveSubscriber_deactivates() {
      Subscriber existingSubscriber = new Subscriber();
      existingSubscriber.setActive(true);
      when(subscriberRepository.findByPhoneNumberHash(PHONE_NUMBER_HASH)).thenReturn(Optional.of(existingSubscriber));
      when(subscriberRepository.save(any(Subscriber.class))).thenReturn(existingSubscriber);

      subscriberService.manageSubscription(PHONE_NUMBER, SubscriptionCommand.STOP);

      verify(subscriberRepository, times(1)).save(subscriberCaptor.capture());
      Subscriber savedSubscriber = subscriberCaptor.getValue();

      assertFalse(savedSubscriber.isActive());
      assertEquals(existingSubscriber, savedSubscriber);
    }

    @Test
    @DisplayName("Should not save an already active subscriber on START command")
    void manageSubscription_startForActiveSubscriber_doesNothing() {
      Subscriber existingSubscriber = new Subscriber();
      existingSubscriber.setActive(true);
      when(subscriberRepository.findByPhoneNumberHash(PHONE_NUMBER_HASH)).thenReturn(Optional.of(existingSubscriber));

      subscriberService.manageSubscription(PHONE_NUMBER, SubscriptionCommand.START);

      verify(subscriberRepository, never()).save(any(Subscriber.class));
    }

    @Test
    @DisplayName("Should not save an already inactive subscriber on STOP command")
    void manageSubscription_stopForInactiveSubscriber_doesNothing() {
      Subscriber existingSubscriber = new Subscriber();
      existingSubscriber.setActive(false);
      when(subscriberRepository.findByPhoneNumberHash(PHONE_NUMBER_HASH)).thenReturn(Optional.of(existingSubscriber));

      subscriberService.manageSubscription(PHONE_NUMBER, SubscriptionCommand.STOP);

      verify(subscriberRepository, never()).save(any(Subscriber.class));
    }
  }

  @Nested
  @DisplayName("Race Condition Handling")
  class RaceConditionTests {

    @Test
    @DisplayName("Should wrap DataIntegrityViolationException to signal a retry is needed")
    void manageSubscription_wrapsDataIntegrityViolationOnSave() {
      when(subscriberRepository.findByPhoneNumberHash(PHONE_NUMBER_HASH)).thenReturn(Optional.empty());

      when(subscriberRepository.save(any(Subscriber.class)))
          .thenThrow(new DataIntegrityViolationException("Simulated race condition on INSERT"));

      assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
        subscriberService.manageSubscription(PHONE_NUMBER, SubscriptionCommand.START);
      });

      verify(subscriberRepository, times(1)).findByPhoneNumberHash(PHONE_NUMBER_HASH);
      verify(subscriberRepository, times(1)).save(any(Subscriber.class));
    }
  }
}