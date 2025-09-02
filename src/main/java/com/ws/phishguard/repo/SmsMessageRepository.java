package com.ws.phishguard.repo;

import com.ws.phishguard.entity.SmsMessage;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface SmsMessageRepository extends JpaRepository<SmsMessage, UUID> {

  Optional<SmsMessage> findByIdempotencyKey(String idempotencyKey);

  @Modifying
  long deleteByCreatedAtBefore(OffsetDateTime threshold);
}