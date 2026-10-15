package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;

import dev.mtgcmd.catalog.DeckImporter.Draft;
import dev.mtgcmd.catalog.ParsedDeck.Entry;
import dev.mtgcmd.game.Game;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class DeckImporterTest {
  private static final String ISLAND_DECK = "1 Island";

  private static final String ISLAND_NAME = "Island";

  private static final String CATALOG_ERROR = "No catalog";

  private static final String SOL_RING_NAME = "Sol Ring";

  private static final String MOXFIELD_DECK_URL = "https://moxfield.com/decks/x";

  @Test
  void providerJsonFailureRetainsItsCauseAndExistingMessage() {
    final Throwable failure =
        catchThrowable(() -> fromJson("{bad").preview(null, MOXFIELD_DECK_URL));
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
                    if ("Unknown".equals(Objects.requireNonNull(name))) {
                      throw new IllegalArgumentException("Unknown card: Unknown");
                    }
                    return new Game.Definition(name, name, "", "", "", List.of(), true, List.of());
                  })
              .toList();

  @Test
  void textExportsHandleSectionsQuantitiesAndPrintingAnnotations() {
    final DeckImporter importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw new AssertionError("Must not fetch");
            });
    final Draft draft =
        importer.preview(
            "Commander\n1 Gisa and Geralf\n\nDeck\n2 Island (M21) 265\n1 Sol Ring\n\nSideboard\n1 Unknown",
            null);
    assertThat(draft.cards()).hasSize(4);
    assertThat(draft.choices()).hasSize(3);
    assertThat(draft.suggested()).containsExactly("Gisa and Geralf");
    assertThat(draft.cards().get(1).name()).isEqualTo(ISLAND_NAME);
  }

  @Test
  void unknownCardsOversizedDecksAndMalformedLinesFailClearly() {
    final DeckImporter importer = new DeckImporter(catalog, uri -> "");
    assertThatThrownBy(() -> importer.preview("1 Unknown", null)).hasMessageContaining("Unknown");
    assertThatThrownBy(() -> importer.preview("9999 Island", null)).hasMessageContaining("250");
    assertThatThrownBy(() -> importer.preview("-1 Island", null)).hasMessageContaining("line");
    assertThatThrownBy(() -> importer.preview("", null)).hasMessageContaining("empty");
  }

  @Test
  void moxfieldAdapterUsesFixedApiHostAndPreservesCommanderSuggestions() {
    final AtomicReference<String> requested = new AtomicReference<>();
    final DeckImporter importer =
        new DeckImporter(
            catalog,
            uri -> {
              requested.set(uri.toString());
              return "{\"mainboard\":{\"a\":{\"quantity\":2,\"card\":{\"name\":\"Island\"}}},\"commanders\":{\"b\":{\"quantity\":1,\"card\":{\"name\":\"Gisa and Geralf\"}}}}";
            });
    final Draft draft = importer.preview(null, "https://moxfield.com/decks/abc_DEF-123");
    assertThat(requested.get()).isEqualTo("https://api2.moxfield.com/v3/decks/all/abc_DEF-123");
    assertThat(draft.cards()).hasSize(3);
    assertThat(draft.suggested()).containsExactly("Gisa and Geralf");
    for (final String url :
        List.of(
            "http://moxfield.com/decks/x",
            "https://moxfield.com.evil/decks/x",
            "https://localhost/",
            "https://moxfield.com@evil/decks/x",
            "https://moxfield.com:8443/decks/x")) {
      assertThatThrownBy(() -> importer.preview(null, url)).hasMessageContaining("Moxfield");
    }
  }

  @Test
  void unavailableMoxfieldReportsExportFallback() {
    final DeckImporter importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw new CardServiceUnavailableException("403", null);
            });
    assertThatThrownBy(() -> importer.preview(null, "https://moxfield.com/decks/abc"))
        .hasMessageContaining("paste");
    assertThat(importer.preview(ISLAND_DECK, null).cards()).hasSize(1);
  }

  private DeckImporter fromJson(final String json) {
    return new DeckImporter(catalog, uri -> json);
  }

  static Stream<CardServiceException> serviceFailures() {
    final IOException cause = new IOException("provider failure");
    return Stream.of(
        new CardServiceTimeoutException("timeout", cause),
        new CardServiceUnavailableException("offline", cause),
        new InvalidCardServiceResponseException("invalid response", cause),
        new CardServiceInterruptedException("interrupted", new InterruptedException()));
  }

  @ParameterizedTest
  @MethodSource("serviceFailures")
  void moxfieldTranslationPreservesEveryFailureCategoryAndCause(
      final CardServiceException original) {
    final DeckImporter importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw original;
            });
    final Throwable failure = catchThrowable(() -> importer.preview(null, MOXFIELD_DECK_URL));
    assertThat(failure)
        .isExactlyInstanceOf(original.getClass())
        .hasCause(original)
        .hasMessage(
            "Moxfield is unavailable or the deck is private. Export the deck and paste its text instead.");
    assertThat(failure.getCause().getCause()).isSameAs(original.getCause());
  }

  @Test
  void unexpectedFetcherFailureIsNotConvertedIntoProviderUnavailability() {
    final NullPointerException bug = new NullPointerException("fetcher bug");
    final DeckImporter importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw bug;
            });
    assertThat(catchThrowable(() -> importer.preview(null, MOXFIELD_DECK_URL))).isSameAs(bug);
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
  void blankUrlSelectsText(final String url) {
    final DeckImporter importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw new AssertionError("No fetch");
            });
    assertThat(importer.preview(ISLAND_DECK, url).cards()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        MOXFIELD_DECK_URL,
        " https://www.moxfield.com/decks/x/ ",
        "https://moxfield.com/decks/x?q=y#fragment",
        "https://moxfield.com/decks/%78"
      })
  void validUrlsOverrideInvalidText(final String url) {
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
  void invalidUrlsNeverFetch(final String url) {
    final DeckImporter importer =
        new DeckImporter(
            catalog,
            uri -> {
              throw new AssertionError("No fetch");
            });
    assertThatThrownBy(() -> importer.preview(ISLAND_DECK, url))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Moxfield");
  }

  @Test
  void deckIdLengthBoundary() {
    final DeckImporter importer = fromJson("{\"mainboard\":[{\"card\":{\"name\":\"Island\"}}]}");
    assertThat(importer.preview(null, "https://moxfield.com/decks/" + "a".repeat(80)).cards())
        .hasSize(1);
    assertThatThrownBy(() -> importer.preview(null, "https://moxfield.com/decks/" + "a".repeat(81)))
        .hasMessageContaining("Moxfield");
  }

  @ParameterizedTest
  @ValueSource(strings = {"commander", "COMMANDERS:", "com:mander"})
  void sectionsCommentsAndAnnotationsPreserveOrder(final String header) {
    final Draft draft =
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
        .containsExactly(ISLAND_NAME, ISLAND_NAME, SOL_RING_NAME, SOL_RING_NAME, "island");
    assertThat(draft.suggested()).containsExactly(ISLAND_NAME);
    assertThat(draft.choices())
        .extracting(Game.Definition::name)
        .containsExactly(ISLAND_NAME, SOL_RING_NAME, "island");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"mainboard\":[{\"card\":{\"name\":\"Island\"}}]}",
        "{\"boards\":{\"mainboard\":{\"cards\":{\"a\":{\"card\":{\"name\":\"Island\"}}}}}}",
        "{\"commanders\":{\"cards\":[{\"card\":{\"name\":\"Island\"}}]}}"
      })
  void supportedProviderLayouts(final String json) {
    assertThat(fromJson(json).preview(null, MOXFIELD_DECK_URL).cards()).hasSize(1);
  }

  @Test
  void textBoundariesAndFailuresAvoidCatalog() {
    final DeckImporter importer =
        new DeckImporter(
            names -> {
              throw new AssertionError(CATALOG_ERROR);
            },
            uri -> "");
    for (final String text :
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
    final DeckImporter valid = new DeckImporter(catalog, uri -> "");
    assertThat(valid.preview("250 Island", null).cards()).hasSize(250);
    assertThat(valid.preview("1 " + "a".repeat(200), null).cards()).hasSize(1);
    assertThat(valid.preview("1 Island\n#" + "a".repeat(99990), null).cards()).hasSize(1);
  }

  @Test
  void catalogMismatchAndRepeatedCommanders() {
    final DeckImporter importer = new DeckImporter(names -> List.of(), uri -> "");
    assertThatThrownBy(() -> importer.preview(ISLAND_DECK, null))
        .hasMessageContaining("could not be resolved");
    final Draft draft =
        new DeckImporter(catalog, uri -> "").preview("Commanders\n1 Island\n1 Island", null);
    assertThat(draft.suggested()).containsExactly(ISLAND_NAME);
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
  void malformedProviderDataHasActionableErrorsBeforeCatalog(final String json) {
    final DeckImporter importer =
        new DeckImporter(
            names -> {
              throw new AssertionError(CATALOG_ERROR);
            },
            uri -> json);
    assertThatThrownBy(() -> importer.preview(null, MOXFIELD_DECK_URL))
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
  void providerQuantitiesMustBeBoundedIntegers(final String quantity) {
    final DeckImporter importer =
        new DeckImporter(
            names -> {
              throw new AssertionError(CATALOG_ERROR);
            },
            uri ->
                "{\"mainboard\":[{\"quantity\":" + quantity + ",\"card\":{\"name\":\"Island\"}}]}");
    assertThatThrownBy(() -> importer.preview(null, MOXFIELD_DECK_URL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported")
        .hasMessageContaining("paste");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "LONG"})
  void invalidProviderNamesAreRejected(String name) {
    if ("LONG".equals(Objects.requireNonNull(name))) {
      name = "a".repeat(201);
    }
    final String json = "{\"mainboard\":[{\"card\":{\"name\":\"" + name + "\"}}]}";
    final DeckImporter importer =
        new DeckImporter(
            names -> {
              throw new AssertionError(CATALOG_ERROR);
            },
            uri -> json);
    assertThatThrownBy(() -> importer.preview(null, MOXFIELD_DECK_URL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
  }

  @Test
  void malformedBoardCannotBeIgnoredBesideValidBoard() {
    assertThatThrownBy(
            () ->
                fromJson("{\"commanders\":null,\"mainboard\":[{\"card\":{\"name\":\"Island\"}}]}")
                    .preview(null, MOXFIELD_DECK_URL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
  }

  static Stream<String> providerLayoutMatrix() {
    final List<String> layouts = new ArrayList<>();
    for (final boolean nested : List.of(false, true)) {
      for (final boolean wrapped : List.of(false, true)) {
        for (final boolean array : List.of(false, true)) {
          layouts.add(providerLayout(nested, wrapped, array));
        }
      }
    }
    return layouts.stream();
  }

  private static String providerLayout(
      final boolean nested, final boolean wrapped, final boolean array) {
    final String entry = "{\"quantity\":1,\"card\":{\"name\":\"Island\"}}";
    final String cards = array ? "[" + entry + "]" : "{\"a\":" + entry + "}";
    final String board = wrapped ? "{\"cards\":" + cards + "}" : cards;
    final String boards = "{\"commanders\":" + board + ",\"mainboard\":" + board + "}";
    return nested ? "{\"boards\":" + boards + "}" : boards;
  }

  @ParameterizedTest
  @MethodSource("providerLayoutMatrix")
  void allContainerCombinationsPreserveAggregationAndSuggestions(final String json) {
    final Draft draft = fromJson(json).preview(null, MOXFIELD_DECK_URL);
    assertThat(draft.cards())
        .extracting(Game.Definition::name)
        .containsExactly(ISLAND_NAME, ISLAND_NAME);
    assertThat(draft.choices()).hasSize(1);
    assertThat(draft.suggested()).containsExactly(ISLAND_NAME);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"boards\":{}}",
        "{\"mainboard\":[]}",
        "{\"commanders\":{},\"mainboard\":{\"cards\":[]}}"
      })
  void emptySupportedDecksUseNoCardsFallback(final String json) {
    assertThatThrownBy(() -> fromJson(json).preview(null, MOXFIELD_DECK_URL))
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
  void malformedEntryFieldsUseUnsupportedFallback(final String entry) {
    assertThatThrownBy(
            () -> fromJson("{\"mainboard\":[" + entry + "]}").preview(null, MOXFIELD_DECK_URL))
        .hasMessage("Moxfield returned an unsupported deck. Export and paste its text instead.");
  }

  @Test
  void providerNameAndTotalBoundaries() {
    final String entry = "{\"quantity\":250,\"card\":{\"name\":\"" + "a".repeat(200) + "\"}}";
    assertThat(fromJson("{\"mainboard\":[" + entry + "]}").preview(null, MOXFIELD_DECK_URL).cards())
        .hasSize(250);
    final DeckImporter importer =
        new DeckImporter(
            names -> {
              throw new AssertionError(CATALOG_ERROR);
            },
            uri -> "{\"mainboard\":[" + entry + ",{\"card\":{\"name\":\"Island\"}}]}");
    assertThatThrownBy(() -> importer.preview(null, MOXFIELD_DECK_URL))
        .hasMessage("Deck must contain 1–250 cards");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n\t"})
  void emptyTextHasStableMessage(final String text) {
    assertThatThrownBy(() -> new TextDeckParser().parse(text)).hasMessage("Decklist is empty");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", "\r"})
  void textParserSupportsLineEndings(final String separator) {
    final ParsedDeck deck =
        new TextDeckParser()
            .parse(
                "Commanders"
                    + separator
                    + ISLAND_DECK
                    + separator
                    + "Deck"
                    + separator
                    + "1 Sol Ring");
    assertThat(deck.entries())
        .containsExactly(
            new ParsedDeck.Entry(ISLAND_NAME, 1, true),
            new ParsedDeck.Entry(SOL_RING_NAME, 1, false));
  }

  @Test
  void parsedDeckIsImmutableAndAggregatesBeforeExpansion() {
    final List<Entry> entries =
        new ArrayList<>(
            List.of(
                new ParsedDeck.Entry(ISLAND_NAME, 1, false),
                new ParsedDeck.Entry(SOL_RING_NAME, 1, false),
                new ParsedDeck.Entry(ISLAND_NAME, 2, true)));
    final ParsedDeck deck = ParsedDeck.from(entries);
    entries.clear();
    assertThat(deck.entries())
        .containsExactly(
            new ParsedDeck.Entry(ISLAND_NAME, 3, true),
            new ParsedDeck.Entry(SOL_RING_NAME, 1, false));
    assertThatThrownBy(() -> deck.entries().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                ParsedDeck.from(
                    Collections.nCopies(20_000, new ParsedDeck.Entry(ISLAND_NAME, 250, false))))
        .hasMessage("Deck must contain 1–250 cards");
  }
}
