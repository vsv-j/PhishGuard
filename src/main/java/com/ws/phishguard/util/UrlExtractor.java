package com.ws.phishguard.util;

import com.ws.phishguard.exception.UrlExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class UrlExtractor {

  /**
   * A robust regular expression for extracting URLs from text.
   * This pattern is designed to correctly identify URLs while excluding common trailing
   * punctuation like periods, commas, or closing parentheses that are part of the surrounding sentence.
   *
   * <p>It works by matching:
   * <ul>
   *   <li>A word boundary `\b` to ensure we don't match in the middle of a word.</li>
   *   <li>A protocol (http, https, ftp) or 'www.' prefix.</li>
   *   <li>The main body of the URL, which can contain a wide range of valid characters.</li>
   *   <li>A final character that is a valid URL character but not a common punctuation mark.</li>
   * </ul>
   * This approach correctly handles punctuation within the URL itself.
   */
  private static final Pattern URL_PATTERN = Pattern.compile(
      // Use a word boundary `\b` which is cleaner and more accurate than matching whitespace.
      "\\b" +
      // Start of Capturing Group 1 (this will contain the full URL)
      "(" +
      // Protocol (https, http, ftp) or www.
      "(?:(?:https?|ftp):\\/\\/|www\\.)" +
      // The main body of the URL. The comma has been removed from this set to treat it as a delimiter.
      "[\\w\\-.~:/?#\\[\\]@!$&'()*+;=%]*" +
      // The final character of the URL. It must not be common trailing punctuation.
      // This prevents matching trailing dots, commas, etc., but allows them inside the URL.
      "[\\w\\-~/?#\\[\\]@!$&'(*+=%]" +
      ")", // End of Capturing Group 1
      Pattern.CASE_INSENSITIVE);

  public List<String> extractUrls(String text) {
    if (text == null) {
      return List.of();
    }
    try {
      return URL_PATTERN.matcher(text).results()
          .map(matchResult -> matchResult.group(1))
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.error("Failed to extract URLs from text due to an unexpected error. Text length: {}", text.length(), e);
      throw new UrlExtractionException("Failed to process text for URL extraction", e);
    }
  }
}