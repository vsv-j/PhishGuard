package com.ws.phishguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Represents an event to be published to a message broker.
 * This entity is the core of the Transactional Outbox Pattern.
 */
@Getter
@Setter
@Entity
@Table(name = "outbox_events")
@NoArgsConstructor
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String topic;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public OutboxEvent(String topic, String payload) {
    this.topic = topic;
    this.payload = payload;
  }
}