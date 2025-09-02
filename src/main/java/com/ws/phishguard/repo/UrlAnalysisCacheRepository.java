package com.ws.phishguard.repo;

import com.ws.phishguard.entity.UrlAnalysisCache;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID; // Import UUID
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface UrlAnalysisCacheRepository extends JpaRepository<UrlAnalysisCache, UUID> { // Change Long to UUID

  Optional<UrlAnalysisCache> findByUrlHash(String urlHash);

  List<UrlAnalysisCache> findByUrlHashIn(Collection<String> urlHashes);

  @Modifying
  long deleteByLastCheckedAtBefore(OffsetDateTime threshold);
}