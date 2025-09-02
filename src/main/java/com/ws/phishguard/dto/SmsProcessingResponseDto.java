package com.ws.phishguard.dto;

import com.ws.phishguard.service.ProcessingStatus;
import java.util.UUID;

/**
 * Represents the standardized response after processing an incoming SMS.
 *
 * @param messageId The unique ID of the audited SMS message record.
 * @param status    The final business outcome of the processing.
 */
public record SmsProcessingResponseDto(
    UUID messageId,
    ProcessingStatus status
) {
}