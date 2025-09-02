package com.ws.phishguard.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class HashGeneratorTest {

  private HashGenerator hashGenerator;

  @BeforeEach
  void setUp() {
    hashGenerator = new HashGenerator();
  }

  @Test
  @DisplayName("Positive: Should return the correct SHA-256 hash for a known string")
  void shouldReturnCorrectHashForKnownInput() {
    String input = "hello world";
    String expectedHash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";

    String actualHash = hashGenerator.hash(input);

    assertThat(actualHash)
        .isNotNull()
        .hasSize(64)
        .isEqualTo(expectedHash);
  }

  @Test
  @DisplayName("Negative: Should return null when the input is null")
  void shouldReturnNullForNullInput() {
    String actualHash = hashGenerator.hash(null);
    assertThat(actualHash).isNull();
  }

  @Test
  @DisplayName("Edge Case: Should return the correct SHA-256 hash for an empty string")
  void shouldReturnCorrectHashForEmptyString() {
    String input = "";
    String expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    String actualHash = hashGenerator.hash(input);
    assertThat(actualHash).isEqualTo(expectedHash);
  }

  @Test
  @DisplayName("Consistency: Should return the same hash for the same input every time")
  void shouldBeConsistentForSameInput() {
    String input = "consistency-check-123";
    String firstHash = hashGenerator.hash(input);
    String secondHash = hashGenerator.hash(input);
    assertThat(firstHash)
        .isNotNull()
        .isEqualTo(secondHash);
  }

  @Test
  @DisplayName("Collision Resistance: Should produce different hashes for different inputs")
  void shouldProduceDifferentHashesForDifferentInputs() {
    String input1 = "this is the first input string";
    String input2 = "this is the second input string"; // A slightly different input

    String hash1 = hashGenerator.hash(input1);
    String hash2 = hashGenerator.hash(input2);

    assertThat(hash1).isNotNull();
    assertThat(hash2).isNotNull();
    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  @DisplayName("Stress Test: Should not produce any collisions for a large set of unique inputs")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldNotHaveCollisionsForLargeNumberOfUniqueInputs() {
    final int numberOfHashes = 100_000;
    Set<String> generatedHashes = new HashSet<>(numberOfHashes);

    for (int i = 0; i < numberOfHashes; i++) {
      String uniqueInput = "test-input-" + i;
      String hash = hashGenerator.hash(uniqueInput);
      generatedHashes.add(hash);
    }

    assertThat(generatedHashes).hasSize(numberOfHashes);
  }

  @Test
  @DisplayName("Thread Safety: Should handle concurrent access without errors and produce consistent results")
  void shouldHandleConcurrentAccessSafely() throws InterruptedException {
    final int threadCount = 100;
    final String concurrentInput = "thread-safety-test";
    final String expectedHash = hashGenerator.hash(concurrentInput);

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    List<Callable<String>> tasks = IntStream.range(0, threadCount)
        .mapToObj(i -> (Callable<String>) () -> hashGenerator.hash(concurrentInput))
        .collect(Collectors.toList());

    List<String> results = executorService.invokeAll(tasks)
        .stream()
        .map(future -> {
          try {
            return future.get();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        })
        .collect(Collectors.toList());

    assertThat(results)
        .hasSize(threadCount)
        .allMatch(hash -> hash.equals(expectedHash));
  }
}