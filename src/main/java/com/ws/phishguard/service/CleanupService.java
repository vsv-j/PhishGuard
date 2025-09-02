package com.ws.phishguard.service;

import com.ws.phishguard.repo.SmsMessageRepository;
import com.ws.phishguard.repo.UrlAnalysisCacheRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CleanupService {

  private final UrlAnalysisCacheRepository urlAnalysisCacheRepository;
  private final SmsMessageRepository smsMessageRepository;

  @Value("${phishguard.cache.retention-days:30}")
  private int cacheRetentionDays;

  @Value("${phishguard.messages.retention-days:180}")
  private int messageRetentionDays;

  /**
   * Periodically purges old URL analysis results from the database cache.
   */
  @Scheduled(cron = "${phishguard.cache.cleanup-cron:0 0 6 * * ?}")
  @SchedulerLock(name = "purgeOldCacheEntriesTask", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1M")
  @Transactional
  public void purgeOldCacheEntries() {
    if (cacheRetentionDays <= 0) {
      log.info("Cache retention policy is disabled (retention-days <= 0). Skipping cleanup.");
      return;
    }

    OffsetDateTime threshold = OffsetDateTime.now().minusDays(cacheRetentionDays);
    log.info("Purging URL analysis cache entries older than {} days (before {}).", cacheRetentionDays, threshold);

    try {
      long deletedCount = urlAnalysisCacheRepository.deleteByLastCheckedAtBefore(threshold);
      log.info("Successfully purged {} old cache entries.", deletedCount);
    } catch (Exception e) {
      log.error("Failed to purge old cache entries due to an error.", e);
    }
  }

  /**
   * Periodically purges old SMS messages from the database to comply with data retention policies.
   */
  @Scheduled(cron = "${phishguard.messages.cleanup-cron:0 0 5 * * ?}")
  @SchedulerLock(name = "purgeOldMessagesTask", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5M")
  @Transactional
  public void purgeOldMessages() {
    if (messageRetentionDays <= 0) {
      log.info("Message retention policy is disabled (retention-days <= 0). Skipping cleanup.");
      return;
    }

    OffsetDateTime threshold = OffsetDateTime.now().minusDays(messageRetentionDays);
    log.info("Purging SMS messages older than {} days (before {}).", messageRetentionDays, threshold);

    try {
      long deletedCount = smsMessageRepository.deleteByCreatedAtBefore(threshold);
      log.info("Successfully purged {} old SMS messages.", deletedCount);
    } catch (Exception e) {
      log.error("Failed to purge old SMS messages due to an error.", e);
    }
  }
}