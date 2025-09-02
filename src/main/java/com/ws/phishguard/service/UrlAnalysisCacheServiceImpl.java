package com.ws.phishguard.service;

import com.ws.phishguard.entity.UrlAnalysisCache;
import com.ws.phishguard.metrics.AppMetrics;
import com.ws.phishguard.repo.UrlAnalysisCacheRepository;
import com.ws.phishguard.service.ext.dto.UrlAnalysisStatus;
import com.ws.phishguard.util.HashGenerator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UrlAnalysisCacheServiceImpl implements UrlAnalysisCacheService {

  private final UrlAnalysisCacheRepository urlAnalysisCacheRepository;
  private final UrlThreatDecisionService urlThreatDecisionService;
  private final HashGenerator hashGenerator;
  private final CacheManager cacheManager;
  private final AppMetrics appMetrics;

  @Override
  public boolean containsPhishingUrl(List<String> urls) {
    return appMetrics.URL_ANALYSIS_LATENCY().record(() -> {
      if (urls == null || urls.isEmpty()) {
        return false;
      }
      List<String> distinctUrls = urls.stream().distinct().toList();

      // Step 1: Check L1 Cache (Caffeine) for all URLs.
      CacheCheckResult l1Result = checkL1Cache(distinctUrls);
      if (l1Result.phishingFound()) {
        log.debug("Phishing URL found in L1 cache. Short-circuiting analysis.");
        return true;
      }
      if (l1Result.remainingUrls().isEmpty()) {
        log.debug("All URLs were found in L1 cache and are safe.");
        return false;
      }

      // Step 2: Check L2 Cache (Database) in a single batch.
      CacheCheckResult l2Result = checkL2Cache(l1Result.remainingUrls());
      if (l2Result.phishingFound()) {
        log.debug("Phishing URL found in L2 cache. Short-circuiting analysis.");
        return true;
      }
      if (l2Result.remainingUrls().isEmpty()) {
        log.debug("All remaining URLs were found in L2 cache and are safe.");
        return false;
      }

      // Step 3: For remaining URLs, check externally and explicitly update L1 cache.
      List<String> urlsForExternalApi = l2Result.remainingUrls();
      log.debug("{} URLs not in any cache. Checking with external service.", urlsForExternalApi.size());
      Cache l1Cache = cacheManager.getCache("urlAnalysis");
      for (String url : urlsForExternalApi) {
        Optional<Boolean> phishingResultOpt = checkExternallyAndPersist(url);

        if (phishingResultOpt.isPresent()) {
          boolean isPhishing = phishingResultOpt.get();
          if (l1Cache != null) {
            l1Cache.put(url, isPhishing);
          }
          if (isPhishing) {
            return true;
          }
        }
      }
      return false;
    });
  }

  private CacheCheckResult checkL1Cache(List<String> urls) {
    Cache l1Cache = cacheManager.getCache("urlAnalysis");
    if (l1Cache == null) {
      log.warn("L1 Cache 'urlAnalysis' not found. Bypassing L1 check.");
      return new CacheCheckResult(false, urls);
    }

    List<String> urlsNotFoundInCache = new ArrayList<>();
    for (String url : urls) {
      Cache.ValueWrapper cachedValue = l1Cache.get(url);
      if (cachedValue == null) {
        appMetrics.URL_CACHE_OUTCOMES("L1", "miss").increment();
        urlsNotFoundInCache.add(url);
      } else {
        appMetrics.URL_CACHE_OUTCOMES("L1", "hit").increment();
        boolean isPhishing = (boolean) cachedValue.get();
        if (isPhishing) {
          log.debug("L1 cache hit for URL [{}]: PHISHING. Short-circuiting.", url);
          return new CacheCheckResult(true, List.of());
        }
      }
    }
    return new CacheCheckResult(false, urlsNotFoundInCache);
  }

  private CacheCheckResult checkL2Cache(List<String> urls) {
    log.debug("{} URLs not in L1 cache. Checking L2 cache.", urls.size());
    Map<String, String> urlToHashMap =
        urls.stream().collect(Collectors.toMap(Function.identity(), hashGenerator::hash));

    List<UrlAnalysisCache> dbResults = urlAnalysisCacheRepository.findByUrlHashIn(urlToHashMap.values());

    Map<String, UrlAnalysisCache> hashToResultMap = dbResults.stream()
        .collect(Collectors.toMap(UrlAnalysisCache::getUrlHash, Function.identity()));

    Cache l1Cache = cacheManager.getCache("urlAnalysis");
    List<String> urlsForExternalApi = new ArrayList<>();
    boolean phishingFound = false;

    for (String url : urls) {
      String hash = urlToHashMap.get(url);
      if (hashToResultMap.containsKey(hash)) {
        appMetrics.URL_CACHE_OUTCOMES("L2", "hit").increment();
        UrlAnalysisCache result = hashToResultMap.get(hash);
        boolean isPhishing = result.isPhishing();

        if (l1Cache != null) {
          l1Cache.put(url, isPhishing);
        }

        if (isPhishing) {
          phishingFound = true;
        }
      } else {
        appMetrics.URL_CACHE_OUTCOMES("L2", "miss").increment();
        urlsForExternalApi.add(url);
      }
    }

    if (phishingFound) {
      log.debug("Phishing URL found in L2 cache. Short-circuiting analysis.");
      return new CacheCheckResult(true, List.of());
    }

    return new CacheCheckResult(false, urlsForExternalApi);
  }

  private Optional<Boolean> checkExternallyAndPersist(String url) {
    log.debug("No cache hit for URL: {}. Determining threat status externally.", url);
    UrlAnalysisStatus status = urlThreatDecisionService.determineUrlStatus(url);

    if (status == UrlAnalysisStatus.THREAT_FOUND || status == UrlAnalysisStatus.SAFE) {
      boolean isPhishing = (status == UrlAnalysisStatus.THREAT_FOUND);
      String urlHash = hashGenerator.hash(url);
      saveResultToCache(urlHash, isPhishing);
      log.debug("Saved conclusive analysis to DB cache for URL: {}", url);
      return Optional.of(isPhishing);
    } else {
      log.warn(
          "Analysis for URL [{}] was not conclusive (Status: {}). Result will not be cached.",
          url,
          status);
      return Optional.empty();
    }
  }

  private void saveResultToCache(String urlHash, boolean isPhishing) {
    UrlAnalysisCache newCacheEntry = new UrlAnalysisCache();
    newCacheEntry.setUrlHash(urlHash);
    newCacheEntry.setPhishing(isPhishing);
    newCacheEntry.setLastCheckedAt(OffsetDateTime.now());
    urlAnalysisCacheRepository.save(newCacheEntry);
  }
}