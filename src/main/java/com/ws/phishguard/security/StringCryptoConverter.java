package com.ws.phishguard.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class StringCryptoConverter implements AttributeConverter<String, String> {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTE = 12;
  private static final int TAG_LENGTH_BIT = 128;

  private final SecureRandom secureRandom = new SecureRandom();
  private final SecretKeySpec key;

  public StringCryptoConverter(@Value("${phishguard.encryption.key}") String encryptionKey) {
    byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
      throw new IllegalArgumentException(
          "Invalid AES key length: " + keyBytes.length + " bytes. Key must be 16, 24, or 32 bytes long.");
    }
    this.key = new SecretKeySpec(keyBytes, "AES");
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_LENGTH_BYTE];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
      cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);

      byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

      ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
      byteBuffer.put(iv);
      byteBuffer.put(cipherText);

      return Base64.getEncoder().encodeToString(byteBuffer.array());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt attribute", e);
    }
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    try {
      byte[] decodedBytes = Base64.getDecoder().decode(dbData);
      ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);

      byte[] iv = new byte[IV_LENGTH_BYTE];
      byteBuffer.get(iv);

      byte[] cipherText = new byte[byteBuffer.remaining()];
      byteBuffer.get(cipherText);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
      cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);

      byte[] decryptedText = cipher.doFinal(cipherText);
      return new String(decryptedText, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decrypt attribute", e);
    }
  }
}