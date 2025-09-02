package com.ws.phishguard.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ws.phishguard.entity.UrlAnalysisCache;
import com.ws.phishguard.metrics.AppMetrics;
import com.ws.phishguard.repo.UrlAnalysisCacheRepository;
import com.ws.phishguard.service.ext.dto.UrlAnalysisStatus;
import com.ws.phishguard.util.HashGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;

@ExtendWith(MockitoExtension.class)
class UrlAnalysisCacheServiceImplTest {

  @Mock
  private UrlAnalysisCacheRepository urlAnalysisCacheRepository;
  @Mock
  private UrlThreatDecisionService urlThreatDecisionService;
  @Mock
  private HashGenerator hashGenerator;
  @Mock
  private CacheManager cacheManager;
  @Mock
  private AppMetrics appMetrics;

  @Mock
  private Cache mockCache;
  @Mock
  private Counter mockCacheOutcomeCounter;

  @InjectMocks
  private UrlAnalysisCacheServiceImpl urlAnalysisCacheService;

  private final Timer realTimer = new SimpleMeterRegistry().timer("test.timer");
  private static final String SAFE_URL_1 = "http://safe.com";
  private static final String SAFE_URL_2 = "http://alsosafe.com";
  private static final String PHISHING_URL = "http://phishing.com";
  private static final String UNKNOWN_URL = "http://unknown.com";

  @BeforeEach
  void setUp() {
    when(appMetrics.URL_ANALYSIS_LATENCY()).thenReturn(realTimer);
    lenient().when(cacheManager.getCache("urlAnalysis")).thenReturn(mockCache);
    lenient().when(hashGenerator.hash(anyString())).thenAnswer(i -> i.getArgument(0) + "-hash");
    lenient().when(appMetrics.URL_CACHE_OUTCOMES(anyString(), anyString())).thenReturn(mockCacheOutcomeCounter);
  }

  @Nested
  @DisplayName("Input Validation")
  class InputValidationTests {
    @Test
    @DisplayName("Should return false for null URL list")
    void containsPhishingUrl_withNullList_returnsFalse() {
      assertFalse(urlAnalysisCacheService.containsPhishingUrl(null));
      verifyNoInteractions(mockCache, urlAnalysisCacheRepository, urlThreatDecisionService);
    }

    @Test
    @DisplayName("Should return false for empty URL list")
    void containsPhishingUrl_withEmptyList_returnsFalse() {
      assertFalse(urlAnalysisCacheService.containsPhishingUrl(Collections.emptyList()));
      verifyNoInteractions(mockCache, urlAnalysisCacheRepository, urlThreatDecisionService);
    }
  }

  @Nested
  @DisplayName("L1 Cache (In-Memory) Scenarios")
  class L1CacheTests {
    @Test
    @DisplayName("Should return true and short-circuit when a phishing URL is in L1 cache")
    void containsPhishingUrl_phishingUrlInL1_returnsTrueAndShortCircuits() {
      when(mockCache.get(SAFE_URL_1)).thenReturn(new SimpleValueWrapper(false));
      when(mockCache.get(PHISHING_URL)).thenReturn(new SimpleValueWrapper(true));

      boolean result = urlAnalysisCacheService.containsPhishingUrl(List.of(SAFE_URL_1, PHISHING_URL, UNKNOWN_URL));

      assertTrue(result);
      verify(mockCache).get(SAFE_URL_1);
      verify(mockCache).get(PHISHING_URL);
      verify(mockCache, never()).get(UNKNOWN_URL);
      verifyNoInteractions(urlAnalysisCacheRepository, urlThreatDecisionService);
      verify(appMetrics, times(2)).URL_CACHE_OUTCOMES("L1", "hit");
    }

    @Test
    @DisplayName("Should return false when all URLs are safe and in L1 cache")
    void containsPhishingUrl_allUrlsSafeInL1_returnsFalse() {
      when(mockCache.get(SAFE_URL_1)).thenReturn(new SimpleValueWrapper(false));
      when(mockCache.get(SAFE_URL_2)).thenReturn(new SimpleValueWrapper(false));

      boolean result = urlAnalysisCacheService.containsPhishingUrl(List.of(SAFE_URL_1, SAFE_URL_2));

      assertFalse(result);
      verify(mockCache, times(2)).get(anyString());
      verifyNoInteractions(urlAnalysisCacheRepository, urlThreatDecisionService);
      verify(appMetrics, times(2)).URL_CACHE_OUTCOMES("L1", "hit");
    }
  }

  @Nested
  @DisplayName("L2 Cache (Database) Scenarios")
  class L2CacheTests {
    @Test
    @DisplayName("Should return true when a phishing URL is in L2 cache and populate L1")
    void containsPhishingUrl_phishingUrlInL2_returnsTrueAndPopulatesL1() {
      when(mockCache.get(anyString())).thenReturn(null);

      String phishingHash = PHISHING_URL + "-hash";
      UrlAnalysisCache phishingDbEntry = new UrlAnalysisCache();
      phishingDbEntry.setUrlHash(phishingHash);
      phishingDbEntry.setPhishing(true);

      String safeHash = SAFE_URL_1 + "-hash";
      UrlAnalysisCache safeDbEntry = new UrlAnalysisCache();
      safeDbEntry.setUrlHash(safeHash);
      safeDbEntry.setPhishing(false);

      when(urlAnalysisCacheRepository.findByUrlHashIn(anyCollection()))
          .thenReturn(List.of(phishingDbEntry, safeDbEntry));

      boolean result = urlAnalysisCacheService.containsPhishingUrl(List.of(SAFE_URL_1, PHISHING_URL));

      assertTrue(result);
      verify(urlAnalysisCacheRepository).findByUrlHashIn(anyCollection());
      verifyNoInteractions(urlThreatDecisionService);
      verify(mockCache).put(SAFE_URL_1, false);
      verify(mockCache).put(PHISHING_URL, true);
      verify(appMetrics, times(2)).URL_CACHE_OUTCOMES("L1", "miss");
      verify(appMetrics, times(2)).URL_CACHE_OUTCOMES("L2", "hit");
    }

    @Test
    @DisplayName("Should return false when all remaining URLs are safe in L2 cache")
    void containsPhishingUrl_allUrlsSafeInL2_returnsFalse() {
      when(mockCache.get(anyString())).thenReturn(null);

      String safeHash1 = SAFE_URL_1 + "-hash";
      UrlAnalysisCache safeDbEntry1 = new UrlAnalysisCache();
      safeDbEntry1.setUrlHash(safeHash1);
      safeDbEntry1.setPhishing(false);

      when(urlAnalysisCacheRepository.findByUrlHashIn(anyCollection()))
          .thenReturn(List.of(safeDbEntry1));

      boolean result = urlAnalysisCacheService.containsPhishingUrl(List.of(SAFE_URL_1));

      assertFalse(result);
      verify(urlAnalysisCacheRepository).findByUrlHashIn(anyCollection());
      verifyNoInteractions(urlThreatDecisionService);
      verify(mockCache).put(SAFE_URL_1, false);
    }
  }

  @Nested
  @DisplayName("External API Check Scenarios")
  class ExternalApiTests {
    @BeforeEach
    void setup() {
      when(mockCache.get(anyString())).thenReturn(null);
      when(urlAnalysisCacheRepository.findByUrlHashIn(anyCollection())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Should return true when external API finds a phishing URL and cache the result")
    void containsPhishingUrl_phishingUrlFoundExternally_returnsTrueAndCachesResult() {
      when(urlThreatDecisionService.determineUrlStatus(SAFE_URL_1)).thenReturn(UrlAnalysisStatus.SAFE);
      when(urlThreatDecisionService.determineUrlStatus(PHISHING_URL)).thenReturn(UrlAnalysisStatus.THREAT_FOUND);

      boolean result = urlAnalysisCacheService.containsPhishingUrl(List.of(SAFE_URL_1, PHISHING_URL, UNKNOWN_URL));

      assertTrue(result);
      verify(urlThreatDecisionService).determineUrlStatus(SAFE_URL_1);
      verify(urlThreatDecisionService).determineUrlStatus(PHISHING_URL);
      verify(urlThreatDecisionService, never()).determineUrlStatus(UNKNOWN_URL);
      verify(mockCache).put(SAFE_URL_1, false);
      verify(mockCache).put(PHISHING_URL, true);
      verify(urlAnalysisCacheRepository).save(argThat(cache -> cache.getUrlHash().equals(SAFE_URL_1 + "-hash") && !cache.isPhishing()));
      verify(urlAnalysisCacheRepository).save(argThat(cache -> cache.getUrlHash().equals(PHISHING_URL + "-hash") && cache.isPhishing()));
    }

    @Test
    @DisplayName("Should return false when external API is inconclusive and not cache the result")
    void containsPhishingUrl_inconclusiveResult_returnsFalseAndDoesNotCache() {
      when(urlThreatDecisionService.determineUrlStatus(SAFE_URL_1)).thenReturn(UrlAnalysisStatus.SAFE);
      when(urlThreatDecisionService.determineUrlStatus(UNKNOWN_URL)).thenReturn(UrlAnalysisStatus.INCONCLUSIVE);

      boolean result = urlAnalysisCacheService.containsPhishingUrl(List.of(SAFE_URL_1, UNKNOWN_URL));

      assertFalse(result);
      verify(urlThreatDecisionService).determineUrlStatus(SAFE_URL_1);
      verify(urlThreatDecisionService).determineUrlStatus(UNKNOWN_URL);
      verify(mockCache).put(SAFE_URL_1, false);
      verify(mockCache, never()).put(eq(UNKNOWN_URL), any());
      verify(urlAnalysisCacheRepository).save(argThat(cache -> cache.getUrlHash().equals(SAFE_URL_1 + "-hash")));
      verify(urlAnalysisCacheRepository, never()).save(argThat(cache -> cache.getUrlHash().equals(UNKNOWN_URL + "-hash")));
    }
  }

  @Nested
  @DisplayName("Edge Case Scenarios")
  class EdgeCaseTests {
    @Test
    @DisplayName("Should handle distinct URLs correctly when duplicates are provided")
    void containsPhishingUrl_withDuplicateUrls_checksEachUrlOnce() {
      when(mockCache.get(SAFE_URL_1)).thenReturn(null);
      when(urlAnalysisCacheRepository.findByUrlHashIn(anyCollection())).thenReturn(Collections.emptyList());
      when(urlThreatDecisionService.determineUrlStatus(SAFE_URL_1)).thenReturn(UrlAnalysisStatus.SAFE);

      boolean result = urlAnalysisCacheService.containsPhishingUrl(List.of(SAFE_URL_1, SAFE_URL_1, SAFE_URL_1));

      assertFalse(result);
      verify(mockCache, times(1)).get(SAFE_URL_1);
      verify(urlAnalysisCacheRepository, times(1)).findByUrlHashIn(anyCollection());
      verify(urlThreatDecisionService, times(1)).determineUrlStatus(SAFE_URL_1);
    }

    @Test
    @DisplayName("Should bypass L1 check if cache is not found and proceed to L2")
    void containsPhishingUrl_whenL1CacheNotFound_bypassesL1AndChecksL2() {
      when(cacheManager.getCache("urlAnalysis")).thenReturn(null);

      String safeHash = SAFE_URL_1 + "-hash";
      UrlAnalysisCache safeDbEntry = new UrlAnalysisCache();
      safeDbEntry.setUrlHash(safeHash);
      safeDbEntry.setPhishing(false);
      when(urlAnalysisCacheRepository.findByUrlHashIn(anyCollection())).thenReturn(List.of(safeDbEntry));

      boolean result = urlAnalysisCacheService.containsPhishingUrl(List.of(SAFE_URL_1));

      assertFalse(result);
      verify(mockCache, never()).get(any());
      verify(mockCache, never()).put(any(), any());
      verify(urlAnalysisCacheRepository).findByUrlHashIn(anyCollection());
      verifyNoInteractions(urlThreatDecisionService);
    }
  }
}