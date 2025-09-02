package com.ws.phishguard.service;

import java.util.List;

public record CacheCheckResult(boolean phishingFound, List<String> remainingUrls) { }
