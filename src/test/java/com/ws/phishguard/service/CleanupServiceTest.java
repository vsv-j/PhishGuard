package com.ws.phishguard.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ws.phishguard.repo.SmsMessageRepository;
import com.ws.phishguard.repo.UrlAnalysisCacheRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTest {

  @Mock
  private UrlAnalysisCacheRepository urlAnalysisCacheRepository;

  @Mock
  private SmsMessageRepository smsMessageRepository;

  @InjectMocks
  private CleanupService cleanupService;

  @Captor
  private ArgumentCaptor<OffsetDateTime> dateTimeCaptor;

  @Nested
  @DisplayName("purgeOldCacheEntries Tests")
  class PurgeOldCacheEntriesTests {

    @Test
    @DisplayName("Should call repository to delete entries older than the retention period")
    void purgeOldCacheEntries_whenEnabled_deletesOldEntries() {
      int retentionDays = 30;
      ReflectionTestUtils.setField(cleanupService, "cacheRetentionDays", retentionDays);
      when(urlAnalysisCacheRepository.deleteByLastCheckedAtBefore(any(OffsetDateTime.class)))
          .thenReturn(123L);

      cleanupService.purgeOldCacheEntries();

      verify(urlAnalysisCacheRepository).deleteByLastCheckedAtBefore(dateTimeCaptor.capture());
      OffsetDateTime capturedThreshold = dateTimeCaptor.getValue();
      long daysDifference = ChronoUnit.DAYS.between(capturedThreshold, OffsetDateTime.now());

      assertTrue(
          daysDifference >= retentionDays - 1 && daysDifference <= retentionDays + 1,
          "Threshold should be approximately " + retentionDays + " days in the past");
    }

    @Test
    @DisplayName("Should do nothing if cache retention is disabled (<= 0)")
    void purgeOldCacheEntries_whenDisabled_doesNothing() {
      ReflectionTestUtils.setField(cleanupService, "cacheRetentionDays", 0);

      cleanupService.purgeOldCacheEntries();

      verify(urlAnalysisCacheRepository, never()).deleteByLastCheckedAtBefore(any());
    }

    @Test
    @DisplayName("Should catch and log exceptions from the repository without failing")
    void purgeOldCacheEntries_whenRepositoryFails_catchesException() {
      ReflectionTestUtils.setField(cleanupService, "cacheRetentionDays", 30);
      when(urlAnalysisCacheRepository.deleteByLastCheckedAtBefore(any(OffsetDateTime.class)))
          .thenThrow(new RuntimeException("Database connection failed"));

      cleanupService.purgeOldCacheEntries();
      verify(urlAnalysisCacheRepository).deleteByLastCheckedAtBefore(any());
    }
  }

  @Nested
  @DisplayName("purgeOldMessages Tests")
  class PurgeOldMessagesTests {

    @Test
    @DisplayName("Should call repository to delete messages older than the retention period")
    void purgeOldMessages_whenEnabled_deletesOldMessages() {
      int retentionDays = 180;
      ReflectionTestUtils.setField(cleanupService, "messageRetentionDays", retentionDays);
      when(smsMessageRepository.deleteByCreatedAtBefore(any(OffsetDateTime.class)))
          .thenReturn(456L);

      cleanupService.purgeOldMessages();

      verify(smsMessageRepository).deleteByCreatedAtBefore(dateTimeCaptor.capture());
      OffsetDateTime capturedThreshold = dateTimeCaptor.getValue();
      long daysDifference = ChronoUnit.DAYS.between(capturedThreshold, OffsetDateTime.now());

      assertTrue(
          daysDifference >= retentionDays - 1 && daysDifference <= retentionDays + 1,
          "Threshold should be approximately " + retentionDays + " days in the past");
    }

    @Test
    @DisplayName("Should do nothing if message retention is disabled (<= 0)")
    void purgeOldMessages_whenDisabled_doesNothing() {
      ReflectionTestUtils.setField(cleanupService, "messageRetentionDays", -1);

      cleanupService.purgeOldMessages();

      verify(smsMessageRepository, never()).deleteByCreatedAtBefore(any());
    }

    @Test
    @DisplayName("Should catch and log exceptions from the repository without failing")
    void purgeOldMessages_whenRepositoryFails_catchesException() {
      ReflectionTestUtils.setField(cleanupService, "messageRetentionDays", 180);
      when(smsMessageRepository.deleteByCreatedAtBefore(any(OffsetDateTime.class)))
          .thenThrow(new RuntimeException("Database connection failed"));

      cleanupService.purgeOldMessages();
      verify(smsMessageRepository).deleteByCreatedAtBefore(any());
    }
  }
}