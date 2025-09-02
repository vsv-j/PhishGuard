package com.ws.phishguard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StringCryptoConverter Tests")
class StringCryptoConverterTest {

  private static final String TEST_ENCRYPTION_KEY = "MySuperSecretKeyForTesting123456";

  private StringCryptoConverter converter;

  @BeforeEach
  void setUp() {
    converter = new StringCryptoConverter(TEST_ENCRYPTION_KEY);
  }

  @Test
  @DisplayName("Constructor should throw IllegalArgumentException for invalid key length")
  void constructor_withInvalidKeyLength_shouldThrowException() {
    String invalidKey = "this-is-not-a-valid-key-length";

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new StringCryptoConverter(invalidKey),
        "Constructor should reject keys of invalid length");

    assertEquals("Invalid AES key length: 30 bytes. Key must be 16, 24, or 32 bytes long.", exception.getMessage());
  }

  @Test
  @DisplayName("Encrypt and Decrypt should return the original value")
  void encryptDecrypt_shouldReturnOriginalValue() {
    String originalValue = "This is a secret message!";

    String encryptedValue = converter.convertToDatabaseColumn(originalValue);
    String decryptedValue = converter.convertToEntityAttribute(encryptedValue);

    assertNotNull(encryptedValue, "Encrypted value should not be null");
    assertNotEquals(originalValue, encryptedValue, "Encrypted value should not be the same as the original");
    assertEquals(originalValue, decryptedValue, "Decrypted value should match the original");
  }

  @Test
  @DisplayName("Encrypting the same value twice should produce different results")
  void encrypt_shouldBeNonDeterministic() {
    String originalValue = "Same message, different encryption";

    String encryptedValue1 = converter.convertToDatabaseColumn(originalValue);
    String encryptedValue2 = converter.convertToDatabaseColumn(originalValue);

    assertNotNull(encryptedValue1);
    assertNotNull(encryptedValue2);
    assertNotEquals(encryptedValue1, encryptedValue2, "Two encryptions of the same value should be different due to random IVs");
  }

  @Test
  @DisplayName("Converting a null attribute should return null")
  void convertToDatabaseColumn_withNull_shouldReturnNull() {
    String encryptedValue = converter.convertToDatabaseColumn(null);

    assertNull(encryptedValue, "Encrypting a null value should result in null");
  }

  @Test
  @DisplayName("Converting null DB data should return null")
  void convertToEntityAttribute_withNull_shouldReturnNull() {
    String decryptedValue = converter.convertToEntityAttribute(null);

    assertNull(decryptedValue, "Decrypting a null value should result in null");
  }

  @Test
  @DisplayName("An empty string should be encrypted and decrypted correctly")
  void encryptDecrypt_withEmptyString_shouldWorkCorrectly() {
    String originalValue = "";

    String encryptedValue = converter.convertToDatabaseColumn(originalValue);
    String decryptedValue = converter.convertToEntityAttribute(encryptedValue);

    assertNotNull(encryptedValue);
    assertNotEquals(originalValue, encryptedValue);
    assertEquals(originalValue, decryptedValue, "An empty string should decrypt back to an empty string");
  }

  @Test
  @DisplayName("Decrypting tampered data should throw IllegalStateException")
  void convertToEntityAttribute_withTamperedData_shouldThrowException() {
    String originalValue = "This data will be tampered with.";
    String encryptedValue = converter.convertToDatabaseColumn(originalValue);

    byte[] encryptedBytes = Base64.getDecoder().decode(encryptedValue);
    encryptedBytes[encryptedBytes.length - 1] ^= 1;
    String tamperedData = Base64.getEncoder().encodeToString(encryptedBytes);

    assertThrows(IllegalStateException.class,
        () -> converter.convertToEntityAttribute(tamperedData),
        "Decrypting tampered data should throw an exception");
  }

  @Test
  @DisplayName("Decrypting invalid Base64 data should throw IllegalStateException")
  void convertToEntityAttribute_withInvalidBase64_shouldThrowException() {
    String invalidBase64Data = "This is not valid Base64 data!";

    assertThrows(IllegalStateException.class,
        () -> converter.convertToEntityAttribute(invalidBase64Data),
        "Decrypting invalid Base64 data should throw an exception");
  }
}