package com.ws.phishguard.exception;

import com.ws.phishguard.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponseDto> handleValidationExceptions(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.joining(", "));

    log.warn("Validation error for request {}: {}", request.getRequestURI(), message);
    return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request);
  }

  private ResponseEntity<ErrorResponseDto> buildErrorResponse(HttpStatus status, String message,
      HttpServletRequest request) {
    ErrorResponseDto errorResponse = new ErrorResponseDto(
        OffsetDateTime.now(),
        status.value(),
        status.getReasonPhrase(),
        message,
        request.getRequestURI()
    );
    return new ResponseEntity<>(errorResponse, status);
  }
}