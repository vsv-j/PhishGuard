package com.ws.phishguard.repo;

import com.ws.phishguard.entity.Subscriber;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, UUID> {

  Optional<Subscriber> findByPhoneNumberHash(String phoneNumberHash);
}