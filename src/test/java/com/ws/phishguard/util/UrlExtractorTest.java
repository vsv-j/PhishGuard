package com.ws.phishguard.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("URL Extractor Tests")
class UrlExtractorTest {

  private UrlExtractor urlExtractor;

  @BeforeEach
  void setUp() {
    urlExtractor = new UrlExtractor();
  }

  @Test
  @DisplayName("Should return an empty list for null input")
  void shouldReturnEmptyListForNullInput() {
    List<String> result = urlExtractor.extractUrls(null);
    assertThat(result).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("Should return an empty list for an empty string")
  void shouldReturnEmptyListForEmptyInput() {
    List<String> result = urlExtractor.extractUrls("");
    assertThat(result).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("Should return an empty list for text with no URLs")
  void shouldReturnEmptyListForTextWithNoUrls() {
    String text = "This is a simple message with no links in it. Just some regular text.";
    List<String> result = urlExtractor.extractUrls(text);
    assertThat(result).isNotNull().isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "this is not a url: example.com",
      "email@example.com is not a url",
      "http:/missingaslash.com",
      "ftp//wrongprotocol.org",
      "just.some.text",
      "thisisnotwww.example.com"
  })
  @DisplayName("Should not extract malformed or partial URLs")
  void shouldNotExtractMalformedUrls(String text) {
    List<String> result = urlExtractor.extractUrls(text);
    assertThat(result).isEmpty();
  }

  static Stream<Arguments> singleUrlProvider() {
    return Stream.of(
        Arguments.of("A simple http url: http://example.com", List.of("http://example.com")),
        Arguments.of("A secure https url: https://example.com", List.of("https://example.com")),
        Arguments.of("A www url: www.example.com", List.of("www.example.com")),
        Arguments.of("An ftp url: ftp://files.example.com", List.of("ftp://files.example.com")),
        Arguments.of("A url with a path https://example.com/path/to/resource", List.of("https://example.com/path/to/resource")),
        Arguments.of("A url with query params https://example.com/search?q=test&p=1", List.of("https://example.com/search?q=test&p=1")),
        Arguments.of("A url with a fragment https://example.com/page#section-one", List.of("https://example.com/page#section-one")),
        Arguments.of("A url with a port http://localhost:8080/api", List.of("http://localhost:8080/api")),
        Arguments.of("A url with many subdomains www.sub.domain.example.co.uk/page", List.of("www.sub.domain.example.co.uk/page")),
        Arguments.of("A url with mixed case HTTP://EXAMPLE.COM/Path", List.of("HTTP://EXAMPLE.COM/Path")),
        Arguments.of("A url at the start www.start.com is a test", List.of("www.start.com")),
        Arguments.of("A url in the middle, like https://middle.com, is a test", List.of("https://middle.com")),
        Arguments.of("A test with a url at the end www.end.com", List.of("www.end.com"))
    );
  }

  @ParameterizedTest(name = "[{index}] Should extract from: {0}")
  @MethodSource("singleUrlProvider")
  @DisplayName("Should extract a single valid URL from text")
  void shouldExtractSingleValidUrl(String text, List<String> expectedUrls) {
    List<String> result = urlExtractor.extractUrls(text);
    assertThat(result).containsExactlyElementsOf(expectedUrls);
  }

  @Test
  @DisplayName("Should extract two URLs from text")
  void shouldExtractTwoUrls() {
    String text = "Check out http://first.com and also www.second.org.";
    List<String> result = urlExtractor.extractUrls(text);
    assertThat(result).containsExactlyInAnyOrder("http://first.com", "www.second.org");
  }

  @Test
  @DisplayName("Should extract three URLs separated by different delimiters")
  void shouldExtractThreeUrls() {
    String text = "Here are some links: https://one.com,www.two.net\nand ftp://three.io.";
    List<String> result = urlExtractor.extractUrls(text);
    assertThat(result).containsExactlyInAnyOrder("https://one.com", "www.two.net", "ftp://three.io");
  }

  @Test
  @DisplayName("Should extract ten URLs from a long message")
  void shouldExtractTenUrls() {
    String text = "Here is a list: " +
                  "1. http://a.com " +
                  "2. https://b.org/path " +
                  "3. www.c.net?q=1 " +
                  "4. ftp://d.edu#frag " +
                  "5. http://e.co.uk:8080 " +
                  "6. www.f-g.com " +
                  "7. https://h.io/1/2/3 " +
                  "8. www.i.info " +
                  "9. http://j.dev/api " +
                  "10. https://k.ly/short";
    List<String> result = urlExtractor.extractUrls(text);
    assertThat(result).hasSize(10)
        .containsExactlyInAnyOrder(
            "http://a.com",
            "https://b.org/path",
            "www.c.net?q=1",
            "ftp://d.edu#frag",
            "http://e.co.uk:8080",
            "www.f-g.com",
            "https://h.io/1/2/3",
            "www.i.info",
            "http://j.dev/api",
            "https://k.ly/short"
        );
  }

  @Test
  @DisplayName("Should not include trailing punctuation in the URL")
  void shouldNotIncludeTrailingPunctuation() {
    String text = "Check these links: (https://example.com). Is www.test.com/path?q=1. a good site? Visit http://another.com, please.";
    List<String> result = urlExtractor.extractUrls(text);
    assertThat(result).containsExactlyInAnyOrder(
        "https://example.com",
        "www.test.com/path?q=1",
        "http://another.com"
    );
  }

  @Test
  @DisplayName("Should correctly extract a URL that is the entire string")
  void shouldExtractUrlThatIsTheEntireString() {
    String text = "https://www.just-a-url.com/with/a/long/path?and=params";
    List<String> result = urlExtractor.extractUrls(text);
    assertThat(result).containsExactly("https://www.just-a-url.com/with/a/long/path?and=params");
  }
}