package com.ws.phishguard.entity;

import com.ws.phishguard.security.StringCryptoConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "sms_messages",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sms_idempotency_key", columnNames = "idempotency_key")
    }
)
public class SmsMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Version
  private Long version;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Convert(converter = StringCryptoConverter.class)
  @Column(nullable = false)
  private String sender;

  @Convert(converter = StringCryptoConverter.class)
  @Column(nullable = false)
  private String recipient;

  @Convert(converter = StringCryptoConverter.class)
  @Column(columnDefinition = "TEXT", nullable = false)
  private String messageContent;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MessageStatus status;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SmsMessage that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : getClass().hashCode();
  }
}