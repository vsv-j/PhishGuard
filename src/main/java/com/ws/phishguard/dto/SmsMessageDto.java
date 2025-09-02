package com.ws.phishguard.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents the public-facing DTO for an audited SMS message.
 * This record is used for API responses to provide details about a processed message.
 *
 * @param id             The unique identifier of the message record.
 * @param sender         The phone number of the message sender.
 * @param recipient      The phone number of the message recipient.
 * @param messageContent The text content of the SMS.
 * @param status         The final processing status of the message (e.g., "ALLOWED_ANALYZED_SAFE", "REJECTED_PHISHING").
 * @param createdAt      The timestamp when the message was first received and recorded.
 * @param updatedAt      The timestamp when the message record was last updated.
 */
public record SmsMessageDto(
    UUID id,
    String sender,
    String recipient,
    String messageContent,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}