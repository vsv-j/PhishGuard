package com.ws.phishguard.metrics;

import com.ws.phishguard.service.ProcessingStatus;
import com.ws.phishguard.service.kafka.KafkaConsumerOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppMetrics {

  private static final String EXCEPTION = "exception";
  private static final String STATUS = "status";
  private static final String CACHE_LEVEL = "level";
  private static final String OUTCOME = "outcome";

  private final MeterRegistry meterRegistry;

  public Counter SMS_PROCESSED(ProcessingStatus status) {
    return Counter.builder("phishguard.sms.processed.total")
        .description("Counts the number of processed SMS messages by their final status.")
        .tag(STATUS, status.name())
        .register(meterRegistry);
  }

  public Timer SMS_PROCESSING_LATENCY() {
    return Timer.builder("phishguard.sms.processing.latency")
        .description("Measures the latency of end-to-end SMS processing.")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry);
  }

  public Counter URL_CACHE_OUTCOMES(String cacheLevel, String outcome) {
    return Counter.builder("phishguard.url.analysis.cache.access")
        .description("Counts cache hits and misses for URL analysis.")
        .tag(CACHE_LEVEL, cacheLevel)
        .tag(OUTCOME, outcome)
        .register(meterRegistry);
  }

  public Timer URL_ANALYSIS_LATENCY() {
    return Timer.builder("phishguard.url.analysis.latency")
        .description("Measures the latency of the entire URL phishing analysis pipeline.")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry);
  }

  public Timer WEB_RISK_API_LATENCY() {
    return Timer.builder("phishguard.webrisk.api.latency")
        .description("Measures the latency of calls to the Google Web Risk API.")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry);
  }

  public Counter WEB_RISK_API_FAILURES(Class<? extends Throwable> exceptionClazz) {
    return Counter.builder("phishguard.webrisk.api.failures")
        .description("Counts the total number of failures when calling the Google Web Risk API.")
        .tag(EXCEPTION, exceptionClazz.getSimpleName())
        .register(meterRegistry);
  }


  public Counter KAFKA_ANALYSIS_CONSUMER_OUTCOME(KafkaConsumerOutcome outcome) {
    return Counter.builder("phishguard.kafka.consumer.analysis.outcomes")
        .description("Counts the outcomes of messages processed by the SMS Analysis consumer.")
        .tag(OUTCOME, outcome.getTagName())
        .register(meterRegistry);
  }
}