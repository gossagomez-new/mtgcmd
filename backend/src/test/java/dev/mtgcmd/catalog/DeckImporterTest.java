package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;

import dev.mtgcmd.game.Game;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class DeckImporterTest {
  @Test
  void providerJsonFailureRetainsItsCauseAndExistingMessage() {
    Throwable failure =
        catchThrowable(() -> fromJson("{bad").preview(null, "https://moxfield.com/decks/x"));
    assertThat(failure)
        .hasMessage(
            "Moxfield is unavailable or the deck is private. Export the deck and paste its text instead.")
        .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    assertThat(failure.getClass().getSimpleName()).isEqualTo("InvalidCardServiceResponseException");
  }

  DeckImporter.Catalog catalog =
      names ->
          names.stream()
              .map(
                  name -> {
                    if (name.equals("Unknown"))
                      throw new IllegalArgumentException("Unknown card: Unknown");
                    return new Game.Definition(name, name, "", "", "", List.of(), true, List.of());
                  })
              .toList();

  @Test
  void textExportsHandleSectionsQuantitiesAndPrintingAnnotations() {
    var importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw new AssertionError("Must not fetch");
            });
    var draft =
        importer.preview(
            "Commander\n1 Gisa and Geralf\n\nDeck\n2 Island (M21) 265\n1 Sol Ring\n\nSideboard\n1 Unknown",
            null);
    assertThat(draft.cards()).hasSize(4);
    assertThat(draft.choices()).hasSize(3);
    assertThat(draft.suggested()).containsExactly("Gisa and Geralf");
    assertThat(draft.cards().get(1).name()).isEqualTo("Island");
  }

  @Test
  void unknownCardsOversizedDecksAndMalformedLinesFailClearly() {
    var importer = new DeckImporter(catalog, uri -> "");
    assertThatThrownBy(() -> importer.preview("1 Unknown", null)).hasMessageContaining("Unknown");
    assertThatThrownBy(() -> importer.preview("9999 Island", null)).hasMessageContaining("250");
    assertThatThrownBy(() -> importer.preview("-1 Island", null)).hasMessageContaining("line");
    assertThatThrownBy(() -> importer.preview("", null)).hasMessageContaining("empty");
  }

  @Test
  void moxfieldAdapterUsesFixedApiHostAndPreservesCommanderSuggestions() {
    var requested = new AtomicReference<String>();
    var importer =
        new DeckImporter(
            catalog,
            uri -> {
              requested.set(uri.toString());
              return "{\"mainboard\":{\"a\":{\"quantity\":2,\"card\":{\"name\":\"Island\"}}},\"commanders\":{\"b\":{\"quantity\":1,\"card\":{\"name\":\"Gisa and Geralf\"}}}}";
            });
    var draft = importer.preview(null, "https://moxfield.com/decks/abc_DEF-123");
    assertThat(requested.get()).isEqualTo("https://api2.moxfield.com/v3/decks/all/abc_DEF-123");
    assertThat(draft.cards()).hasSize(3);
    assertThat(draft.suggested()).containsExactly("Gisa and Geralf");
    for (String url :
        List.of(
            "http://moxfield.com/decks/x",
            "https://moxfield.com.evil/decks/x",
            "https://localhost/",
            "https://moxfield.com@evil/decks/x",
            "https://moxfield.com:8443/decks/x"))
      assertThatThrownBy(() -> importer.preview(null, url)).hasMessageContaining("Moxfield");
  }

  @Test
  void unavailableMoxfieldReportsExportFallback() {
    var importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw new CardServiceUnavailableException("403", null);
            });
    assertThatThrownBy(() -> importer.preview(null, "https://moxfield.com/decks/abc"))
        .hasMessageContaining("paste");
    assertThat(importer.preview("1 Island", null).cards()).hasSize(1);
  }

  private DeckImporter fromJson(String json) {
    return new DeckImporter(catalog, uri -> json);
  }

  static Stream<CardServiceException> serviceFailures() {
    var cause = new java.io.IOException("provider failure");
    return Stream.of(
        new CardServiceTimeoutException("timeout", cause),
        new CardServiceUnavailableException("offline", cause),
        new InvalidCardServiceResponseException("invalid response", cause),
        new CardServiceInterruptedException("interrupted", new InterruptedException()));
  }

  @ParameterizedTest
  @MethodSource("serviceFailures")
  void moxfieldTranslationPreservesEveryFailureCategoryAndCause(CardServiceException original) {
    var importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw original;
            });
    Throwable failure =
        catchThrowable(() -> importer.preview(null, "https://moxfield.com/decks/x"));
    assertThat(failure)
        .isExactlyInstanceOf(original.getClass())
        .hasCause(original)
        .hasMessage(
            "Moxfield is unavailable or the deck is private. Export the deck and paste its text instead.");
    assertThat(failure.getCause().getCause()).isSameAs(original.getCause());
  }

  @Test
  void unexpectedFetcherFailureIsNotConvertedIntoProviderUnavailability() {
    var bug = new NullPointerException("fetcher bug");
    var importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw bug;
            });
    assertThat(catchThrowable(() -> importer.preview(null, "https://moxfield.com/decks/x")))
        .isSameAs(bug);
  }

  @Test
  void inputParsingRetainsCausesWithoutBecomingAServiceFailure() {
    assertThatThrownBy(() -> MoxfieldDeckUrl.parse("https://moxfield.com/decks/%"))
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid Moxfield URL");
    assertThatThrownBy(() -> new TextDeckParser().parse("999999999999999999999 Island"))
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasCauseInstanceOf(NumberFormatException.class)
        .hasMessage("Deck exceeds 250 cards");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankUrlSelectsText(String url) {
    var importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw new AssertionError("No fetch");
            });
    assertThat(importer.preview("1 Island", url).cards()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://moxfield.com/decks/x",
        " https://www.moxfield.com/decks/x/ ",
        "https://moxfield.com/decks/x?q=y#fragment",
        "https://moxfield.com/decks/%78"
      })
  void validUrlsOverrideInvalidText(String url) {
    assertThat(
            fromJson("{\"mainboard\":[{\"card\":{\"name\":\"Island\"}}]}")
                .preview("invalid", url)
                .cards())
        .hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://moxfield.com/decks/x",
        "https://evil.com/decks/x",
        "https://moxfield.com:443/decks/x",
        "https://user@moxfield.com/decks/x",
        "https://moxfield.com/decks/",
        "https://moxfield.com/decks/x/extra",
        "https://moxfield.com/decks/a.b",
        "not a uri",
        "HTTPS://moxfield.com/decks/x"
      })
  void invalidUrlsNeverFetch(String url) {
    var importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw new AssertionError("No fetch");
            });
    assertThatThrownBy(() -> importer.preview("1 Island", url))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Moxfield");
  }

  @Test
  void deckIdLengthBoundary() {
    var importer = fromJson("{\"mainboard\":[{\"card\":{\"name\":\"Island\"}}]}");
    assertThat(importer.preview(null, "https://moxfield.com/decks/" + "a".repeat(80)).cards())
        .hasSize(1);
    assertThatThrownBy(() -> importer.preview(null, "https://moxfield.com/decks/" + "a".repeat(81)))
        .hasMessageContaining("Moxfield");
  }

  @ParameterizedTest
  @ValueSource(strings = {"commander", "COMMANDERS:", "com:mander"})
  void sectionsCommentsAndAnnotationsPreserveOrder(String header) {
    var draft =
        new DeckImporter(catalog, uri -> "")
            .preview(
                "# comment\r\n// comment\r\n"
                    + header
                    + "\r\n1x Island (M21) 265 *F*\r\n"
                    + "Deck\r\n2 Sol Ring\r\n1 Island\r\nSideboard\r\ninvalid\r\n"
                    + "Maybeboard\r\ninvalid\r\nConsidering\r\ninvalid\r\nMainboard\r\n1 island",
                null);
    assertThat(draft.cards())
        .extracting(Game.Definition::name)
        .containsExactly("Island", "Island", "Sol Ring", "Sol Ring", "island");
    assertThat(draft.suggested()).containsExactly("Island");
    assertThat(draft.choices())
        .extracting(Game.Definition::name)
        .containsExactly("Island", "Sol Ring", "island");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"mainboard\":[{\"card\":{\"name\":\"Island\"}}]}",
        "{\"boards\":{\"mainboard\":{\"cards\":{\"a\":{\"card\":{\"name\":\"Island\"}}}}}}",
        "{\"commanders\":{\"cards\":[{\"card\":{\"name\":\"Island\"}}]}}"
      })
  void supportedProviderLayouts(String json) {
    assertThat(fromJson(json).preview(null, "https://moxfield.com/decks/x").cards()).hasSize(1);
  }

  @Test
  void textBoundariesAndFailuresAvoidCatalog() {
    var importer =
        new DeckImporter(
            names -> {
              throw new AssertionError("No catalog");
            },
            uri -> "");
    for (String text :
        List.of(
            "0 Island",
            "251 Island",
            "250 Island\n1 Island",
            "999999999999 Island",
            "1 " + "a".repeat(201),
            "#".repeat(100001),
            "# only comments",
            "\ninvalid",
            "1X Island")) {
      assertThatThrownBy(() -> importer.preview(text, null))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> importer.preview("\ninvalid", null)).hasMessageContaining("line 2");
    var valid = new DeckImporter(catalog, uri -> "");
    assertThat(valid.preview("250 Island", null).cards()).hasSize(250);
    assertThat(valid.preview("1 " + "a".repeat(200), null).cards()).hasSize(1);
    assertThat(valid.preview("1 Island\n#" + "a".repeat(99990), null).cards()).hasSize(1);
  }

  @Test
  void catalogMismatchAndRepeatedCommanders() {
    var importer = new DeckImporter(names -> List.of(), uri -> "");
    assertThatThrownBy(() -> importer.preview("1 Island", null))
        .hasMessageContaining("could not be resolved");
    var draft =
        new DeckImporter(catalog, uri -> "").preview("Commanders\n1 Island\n1 Island", null);
    assertThat(draft.suggested()).containsExactly("Island");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "null",
        "[]",
        "42",
        "{bad",
        "{\"boards\":null}",
        "{\"boards\":[]}",
        "{\"mainboard\":null}",
        "{\"mainboard\":true}",
        "{\"mainboard\":{\"cards\":null}}",
        "{\"mainboard\":{\"cards\":3}}",
        "{\"mainboard\":[null]}",
        "{\"mainboard\":[3]}",
        "{\"mainboard\":[{\"card\":{\"name\":42}}]}"
      })
  void malformedProviderDataHasActionableErrorsBeforeCatalog(String json) {
    var importer =
        new DeckImporter(
            names -> {
              throw new AssertionError("No catalog");
            },
            uri -> json);
    assertThatThrownBy(() -> importer.preview(null, "https://moxfield.com/decks/x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("paste");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "\"1\"",
        "1.5",
        "true",
        "false",
        "0",
        "-1",
        "251",
        "2147483648",
        "4294967297",
        "\"invalid\""
      })
  void providerQuantitiesMustBeBoundedIntegers(String quantity) {
    var importer =
        new DeckImporter(
            names -> {
              throw new AssertionError("No catalog");
            },
            uri ->
                "{\"mainboard\":[{\"quantity\":" + quantity + ",\"card\":{\"name\":\"Island\"}}]}");
    assertThatThrownBy(() -> importer.preview(null, "https://moxfield.com/decks/x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported")
        .hasMessageContaining("paste");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "LONG"})
  void invalidProviderNamesAreRejected(String name) {
    if (name.equals("LONG")) name = "a".repeat(201);
    String json = "{\"mainboard\":[{\"card\":{\"name\":\"" + name + "\"}}]}";
    var importer =
        new DeckImporter(
            names -> {
              throw new AssertionError("No catalog");
            },
            uri -> json);
    assertThatThrownBy(() -> importer.preview(null, "https://moxfield.com/decks/x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
  }

  @Test
  void malformedBoardCannotBeIgnoredBesideValidBoard() {
    assertThatThrownBy(
            () ->
                fromJson("{\"commanders\":null,\"mainboard\":[{\"card\":{\"name\":\"Island\"}}]}")
                    .preview(null, "https://moxfield.com/decks/x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
  }

  static Stream<String> providerLayoutMatrix() {
    var layouts = new ArrayList<String>();
    for (boolean nested : List.of(false, true)) {
      for (boolean wrapped : List.of(false, true)) {
        for (boolean array : List.of(false, true)) {
          layouts.add(providerLayout(nested, wrapped, array));
        }
      }
    }
    return layouts.stream();
  }

  private static String providerLayout(boolean nested, boolean wrapped, boolean array) {
    String entry = "{\"quantity\":1,\"card\":{\"name\":\"Island\"}}";
    String cards = array ? "[" + entry + "]" : "{\"a\":" + entry + "}";
    String board = wrapped ? "{\"cards\":" + cards + "}" : cards;
    String boards = "{\"commanders\":" + board + ",\"mainboard\":" + board + "}";
    return nested ? "{\"boards\":" + boards + "}" : boards;
  }

  @ParameterizedTest
  @MethodSource("providerLayoutMatrix")
  void allContainerCombinationsPreserveAggregationAndSuggestions(String json) {
    var draft = fromJson(json).preview(null, "https://moxfield.com/decks/x");
    assertThat(draft.cards()).extracting(Game.Definition::name).containsExactly("Island", "Island");
    assertThat(draft.choices()).hasSize(1);
    assertThat(draft.suggested()).containsExactly("Island");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"boards\":{}}",
        "{\"mainboard\":[]}",
        "{\"commanders\":{},\"mainboard\":{\"cards\":[]}}"
      })
  void emptySupportedDecksUseNoCardsFallback(String json) {
    assertThatThrownBy(() -> fromJson(json).preview(null, "https://moxfield.com/decks/x"))
        .hasMessage("Moxfield returned no cards. Export and paste its text instead.");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"card\":null}",
        "{\"card\":[]}",
        "{\"card\":{}}",
        "{\"card\":{\"name\":null}}",
        "{\"card\":{\"name\":true}}"
      })
  void malformedEntryFieldsUseUnsupportedFallback(String entry) {
    assertThatThrownBy(
            () ->
                fromJson("{\"mainboard\":[" + entry + "]}")
                    .preview(null, "https://moxfield.com/decks/x"))
        .hasMessage("Moxfield returned an unsupported deck. Export and paste its text instead.");
  }

  @Test
  void providerNameAndTotalBoundaries() {
    String entry = "{\"quantity\":250,\"card\":{\"name\":\"" + "a".repeat(200) + "\"}}";
    assertThat(
            fromJson("{\"mainboard\":[" + entry + "]}")
                .preview(null, "https://moxfield.com/decks/x")
                .cards())
        .hasSize(250);
    var importer =
        new DeckImporter(
            names -> {
              throw new AssertionError("No catalog");
            },
            uri -> "{\"mainboard\":[" + entry + ",{\"card\":{\"name\":\"Island\"}}]}");
    assertThatThrownBy(() -> importer.preview(null, "https://moxfield.com/decks/x"))
        .hasMessage("Deck must contain 1–250 cards");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n\t"})
  void emptyTextHasStableMessage(String text) {
    assertThatThrownBy(() -> new TextDeckParser().parse(text)).hasMessage("Decklist is empty");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", "\r"})
  void textParserSupportsLineEndings(String separator) {
    var deck =
        new TextDeckParser()
            .parse(
                "Commanders"
                    + separator
                    + "1 Island"
                    + separator
                    + "Deck"
                    + separator
                    + "1 Sol Ring");
    assertThat(deck.entries())
        .containsExactly(
            new ParsedDeck.Entry("Island", 1, true), new ParsedDeck.Entry("Sol Ring", 1, false));
  }

  @Test
  void parsedDeckIsImmutableAndAggregatesBeforeExpansion() {
    var entries =
        new ArrayList<>(
            List.of(
                new ParsedDeck.Entry("Island", 1, false),
                new ParsedDeck.Entry("Sol Ring", 1, false),
                new ParsedDeck.Entry("Island", 2, true)));
    var deck = ParsedDeck.from(entries);
    entries.clear();
    assertThat(deck.entries())
        .containsExactly(
            new ParsedDeck.Entry("Island", 3, true), new ParsedDeck.Entry("Sol Ring", 1, false));
    assertThatThrownBy(() -> deck.entries().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                ParsedDeck.from(
                    Collections.nCopies(20_000, new ParsedDeck.Entry("Island", 250, false))))
        .hasMessage("Deck must contain 1–250 cards");
  }
}
