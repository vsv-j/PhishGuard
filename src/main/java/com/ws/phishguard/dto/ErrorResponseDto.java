package com.ws.phishguard.dto;

import java.time.OffsetDateTime;

/**
 * A DTO for returning API error responses.
 *
 * @param timestamp The time the error occurred.
 * @param status    The HTTP status code.
 * @param error     A short, descriptive error message (e.g., "Bad Request").
 * @param message   A more detailed message about the error.
 * @param path      The request path where the error occurred.
 */
public record ErrorResponseDto(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path
) {
}