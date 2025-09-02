package com.ws.phishguard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "url_analysis_cache")
public class UrlAnalysisCache {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "url_hash", nullable = false, unique = true)
  private String urlHash;

  @Column(name = "is_phishing", nullable = false)
  private boolean isPhishing;

  @Column(name = "last_checked_at", nullable = false)
  private OffsetDateTime lastCheckedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UrlAnalysisCache that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : getClass().hashCode();
  }
}