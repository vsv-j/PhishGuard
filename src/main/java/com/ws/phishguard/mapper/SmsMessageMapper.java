package com.ws.phishguard.mapper;

import com.ws.phishguard.dto.SmsMessageDto;
import com.ws.phishguard.dto.SmsRequestDto;
import com.ws.phishguard.entity.SmsMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SmsMessageMapper {

  SmsMessageDto toDto(SmsMessage smsMessage);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "idempotencyKey", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(source = "message", target = "messageContent")
  SmsMessage toEntity(SmsRequestDto smsRequestDto);
}