package com.ws.phishguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Represents the incoming request for a new SMS message to be processed.
 * Input is validated to ensure it meets the required format.
 *
 * @param sender         The phone number of the message sender. Must be a valid phone number format.
 * @param recipient      The phone number of the message recipient. Must be a valid phone number format.
 * @param message        The text content of the SMS.
 */
public record SmsRequestDto(
    @NotBlank(message = "Sender cannot be blank")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Sender must be a valid phone number format (e.g., +15551234567 or 5551234567)")
    String sender,

    @NotBlank(message = "Recipient cannot be blank")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Recipient must be a valid phone number format (e.g., +15551234567 or 5551234567)")
    String recipient,

    @NotBlank(message = "Message content cannot be blank")
    @Size(max = 4096, message = "Message content cannot exceed 4096 characters")
    String message
) {
}