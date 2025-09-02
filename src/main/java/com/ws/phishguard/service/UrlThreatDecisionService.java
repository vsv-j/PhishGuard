package com.ws.phishguard.service;

import com.ws.phishguard.service.ext.dto.UrlAnalysisStatus;

public interface UrlThreatDecisionService {

  UrlAnalysisStatus determineUrlStatus(String url);
}