package com.ws.phishguard.api;

import com.ws.phishguard.dto.SmsMessageDto;
import com.ws.phishguard.dto.SmsProcessingResponseDto;
import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.mapper.SmsMessageMapper;
import com.ws.phishguard.service.SmsMessageService;
import com.ws.phishguard.service.SmsProcessingService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sms")
@Slf4j
@RequiredArgsConstructor
public class SmsController {

  private final SmsProcessingService smsProcessingService;
  private final SmsMessageService smsMessageService;
  private final SmsMessageMapper smsMessageMapper;

  @PostMapping("/incoming")
  public ResponseEntity<SmsProcessingResponseDto> receiveSms(@Valid @RequestBody SmsRequestDto smsRequest) {
    log.debug("Received SMS from: {} to: {}.", smsRequest.sender(), smsRequest.recipient());
    SmsProcessingResponseDto response = smsProcessingService.processSms(smsRequest);
    return ResponseEntity.accepted().body(response);
  }

  @GetMapping("/{messageId}")
  public ResponseEntity<SmsMessageDto> getMessageStatus(@PathVariable UUID messageId) {
    log.debug("Checking status for messageId: {}", messageId);
    return smsMessageService.findById(messageId)
        .map(smsMessageMapper::toDto)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}