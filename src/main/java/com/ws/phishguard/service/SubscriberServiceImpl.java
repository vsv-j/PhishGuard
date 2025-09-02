package com.ws.phishguard.service;

import com.ws.phishguard.entity.Subscriber;
import com.ws.phishguard.repo.SubscriberRepository;
import com.ws.phishguard.util.HashGenerator;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriberServiceImpl implements SubscriberService {

    private final SubscriberRepository subscriberRepository;
    private final HashGenerator hashGenerator;

    @Override
    public Optional<Subscriber> findByPhoneNumberHash(String phoneNumberHash) {
        return subscriberRepository.findByPhoneNumberHash(phoneNumberHash);
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public void manageSubscription(String phoneNumber, SubscriptionCommand command) {
        String phoneNumberHash = hashGenerator.hash(phoneNumber);
        boolean targetIsActive = (command == SubscriptionCommand.START);
        try {
            Optional<Subscriber> optionalSubscriber = subscriberRepository.findByPhoneNumberHash(phoneNumberHash);
            Subscriber subscriber;
            if (optionalSubscriber.isPresent()) {
                subscriber = optionalSubscriber.get();
                if (subscriber.isActive() == targetIsActive) {
                    log.debug("Subscriber for phone number ending in ...{} already in desired state ({}). No action needed.", getLast4(phoneNumber), targetIsActive ? "ACTIVE" : "INACTIVE");
                } else {
                    log.debug("Updating subscriber status for phone number ending in ...{} to {}", getLast4(phoneNumber), targetIsActive ? "ACTIVE" : "INACTIVE");
                    subscriber.setActive(targetIsActive);
                    subscriberRepository.save(subscriber);
                }
            } else {
                log.debug("No subscriber found for hash. Preparing a new one for phone number ending in ...{}",
                        getLast4(phoneNumber));
                subscriber = new Subscriber();
                subscriber.setPhoneNumber(phoneNumber);
                subscriber.setPhoneNumberHash(phoneNumberHash);
                subscriber.setActive(targetIsActive);
                subscriberRepository.save(subscriber);
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected on save for phone number ending in ...{}. Wrapping exception for retry.", getLast4(phoneNumber), e);
            throw new ObjectOptimisticLockingFailureException("Race condition during save", e);
        }
        log.info("Set subscription for phone number ending in ...{} to: {}", getLast4(phoneNumber),
                targetIsActive ? "ACTIVE" : "INACTIVE");
    }


    private String getLast4(String phoneNumber) {
        return phoneNumber.substring(Math.max(0, phoneNumber.length() - 4));
    }
}