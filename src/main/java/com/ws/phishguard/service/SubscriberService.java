package com.ws.phishguard.service;

import com.ws.phishguard.entity.Subscriber;
import java.util.Optional;

public interface SubscriberService {

  Optional<Subscriber> findByPhoneNumberHash(String phoneNumberHash);

  void manageSubscription(String phoneNumber, SubscriptionCommand command);
}