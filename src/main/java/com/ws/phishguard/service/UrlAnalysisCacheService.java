package com.ws.phishguard.service;

import java.util.List;

public interface UrlAnalysisCacheService {

  /**
   * Efficiently checks a list of URLs for any phishing threats using a layered,
   * batch-oriented approach (L1 -> L2 -> External API).
   *
   * @param urls The list of URLs to check.
   * @return true if any URL in the list is determined to be phishing, false otherwise.
   */
  boolean containsPhishingUrl(List<String> urls);
}