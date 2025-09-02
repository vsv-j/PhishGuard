package com.ws.phishguard.util;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

@Component
public class HashGenerator {

  private final MessageDigest sha256;

  public HashGenerator() {
    try {
      this.sha256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  public synchronized String hash(String input) {
    if (input == null) {
      return null;
    }
    final byte[] hashBytes = sha256.digest(input.getBytes(StandardCharsets.UTF_8));
    return toHexString(hashBytes);
  }

  private String toHexString(byte[] bytes) {
    try (Formatter formatter = new Formatter()) {
      for (byte b : bytes) {
        formatter.format("%02x", b);
      }
      return formatter.toString();
    }
  }
}