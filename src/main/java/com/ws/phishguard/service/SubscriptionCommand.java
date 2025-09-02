package com.ws.phishguard.service;

import java.util.Arrays;
import java.util.Optional;

public enum SubscriptionCommand {
  START,
  STOP;

  public static Optional<SubscriptionCommand> fromString(String text) {
    if (text == null) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(command -> command.name().equalsIgnoreCase(text.trim()))
        .findFirst();
  }
}