package com.ws.phishguard.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ws.phishguard.dto.SmsProcessingResponseDto;
import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.exception.WebRiskApiException;
import com.ws.phishguard.mapper.SmsMessageMapper;
import com.ws.phishguard.security.ApiKeyAuthInterceptor;
import com.ws.phishguard.service.ProcessingStatus;
import com.ws.phishguard.service.SmsMessageService;
import com.ws.phishguard.service.SmsProcessingService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = SmsController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {ApiKeyAuthInterceptor.class}
    )
)
@DisplayName("SmsController Tests")
class SmsControllerTest {

  @TestConfiguration
  static class MockBeanProvider {
    @Bean
    public SmsProcessingService smsProcessingService() {
      return Mockito.mock(SmsProcessingService.class);
    }

    @Bean
    public SmsMessageService smsMessageService() {
      return Mockito.mock(SmsMessageService.class);
    }

    @Bean
    public SmsMessageMapper smsMessageMapper() {
      return Mockito.mock(SmsMessageMapper.class);
    }

    @Bean
    public ApiKeyAuthInterceptor apiKeyAuthInterceptor() {
      return Mockito.mock(ApiKeyAuthInterceptor.class);
    }
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private SmsProcessingService smsProcessingService;

  @Autowired
  private SmsMessageService smsMessageService;

  @Autowired
  private SmsMessageMapper smsMessageMapper;

  @Autowired
  private ApiKeyAuthInterceptor apiKeyAuthInterceptor;

  @BeforeEach
  void setUp() throws Exception {
    reset(apiKeyAuthInterceptor, smsProcessingService, smsMessageService, smsMessageMapper);
    when(apiKeyAuthInterceptor.preHandle(any(), any(), any())).thenReturn(true);
  }

  @Test
  @DisplayName("POST /incoming - Should return 202 Accepted for a valid request to be analyzed asynchronously")
  void receiveSms_withValidRequestForAnalysis_shouldReturn202Accepted() throws Exception {
    SmsRequestDto validRequest = new SmsRequestDto("+15551234567", "+15557654321", "Hello http://example.com");
    // This is what the service actually returns for this asynchronous case
    SmsProcessingResponseDto serviceResponse = new SmsProcessingResponseDto(UUID.randomUUID(), ProcessingStatus.ACCEPTED);

    when(smsProcessingService.processSms(validRequest)).thenReturn(serviceResponse);

    mockMvc.perform(post("/api/v1/sms/incoming")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isAccepted())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.messageId").value(serviceResponse.messageId().toString()))
        .andExpect(jsonPath("$.status").value(serviceResponse.status().toString()));

    verify(apiKeyAuthInterceptor).preHandle(any(), any(), any());
    verify(smsProcessingService).processSms(validRequest);
  }

  @Test
  @DisplayName("POST /incoming - Should return 401 Unauthorized when interceptor blocks request")
  void receiveSms_whenInterceptorBlocks_shouldReturn401() throws Exception {
    SmsRequestDto validRequest = new SmsRequestDto("+15551234567", "+15557654321", "A message");

    when(apiKeyAuthInterceptor.preHandle(any(), any(), any()))
        .thenAnswer(invocation -> {
          HttpServletResponse response = invocation.getArgument(1);
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
          return false;
        });

    mockMvc.perform(post("/api/v1/sms/incoming")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isUnauthorized());

    verify(apiKeyAuthInterceptor).preHandle(any(), any(), any());
    verify(smsProcessingService, never()).processSms(any());
  }

  static Stream<Arguments> invalidRequestProvider() {
    String validSender = "+15551234567";
    String validRecipient = "+15557654321";
    return Stream.of(
        Arguments.of(new SmsRequestDto("", validRecipient, "message"), "Sender cannot be blank", "Sender must be a valid phone number format"),
        Arguments.of(new SmsRequestDto(null, validRecipient, "message"), "Sender cannot be blank", null),
        Arguments.of(new SmsRequestDto("  ", validRecipient, "message"), "Sender cannot be blank", "Sender must be a valid phone number format"),

        Arguments.of(new SmsRequestDto(validSender, "", "message"), "Recipient cannot be blank", "Recipient must be a valid phone number format"),
        Arguments.of(new SmsRequestDto(validSender, null, "message"), "Recipient cannot be blank", null),
        Arguments.of(new SmsRequestDto(validSender, "  ", "message"), "Recipient cannot be blank", "Recipient must be a valid phone number format"),

        Arguments.of(new SmsRequestDto(validSender, validRecipient, ""), "Message content cannot be blank", null),
        Arguments.of(new SmsRequestDto(validSender, validRecipient, null), "Message content cannot be blank", null)
    );
  }

  @ParameterizedTest
  @MethodSource("invalidRequestProvider")
  @DisplayName("POST /incoming - Should return 400 Bad Request for invalid requests")
  void receiveSms_withInvalidRequest_shouldReturn400BadRequest(SmsRequestDto invalidRequest, String message1, String message2) throws Exception {
    var resultActions = mockMvc.perform(post("/api/v1/sms/incoming")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"));

    if (message2 == null) {
      resultActions.andExpect(jsonPath("$.message", containsString(message1)));
    } else {
      resultActions.andExpect(jsonPath("$.message", allOf(containsString(message1), containsString(message2))));
    }

    verify(apiKeyAuthInterceptor).preHandle(any(), any(), any());
    verify(smsProcessingService, never()).processSms(any());
  }
}