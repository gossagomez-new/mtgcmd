package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;

import dev.mtgcmd.game.Game;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DeckImporterTest {
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
              throw new IllegalArgumentException("403");
            });
    assertThatThrownBy(() -> importer.preview(null, "https://moxfield.com/decks/abc"))
        .hasMessageContaining("paste");
    assertThat(importer.preview("1 Island", null).cards()).hasSize(1);
  }
}
