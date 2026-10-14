package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import dev.mtgcmd.game.Game.Definition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DeckRulesTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC);

  private static Definition card(
      String id, String type, String text, boolean legal, String... colors) {
    return new Definition(id, id, "", text, type, List.of(colors), legal, List.of());
  }

  private static Definition leader() {
    return card("Leader", "Legendary Creature", "", true, "R");
  }

  private static Definition basic() {
    return card("Mountain", "Basic Land — Mountain", "", true, "R");
  }

  private static List<String> warnings(List<Definition> deck, List<String> commanders) {
    return DeckRules.validate(GameFormat.COMMANDER, deck, commanders, CLOCK);
  }

  record SelectionCase(List<String> ids, String message) {}

  static Stream<SelectionCase> invalidSelections() {
    return Stream.of(
        new SelectionCase(null, "Choose one or two commanders"),
        new SelectionCase(List.of(), "Choose one or two commanders"),
        new SelectionCase(List.of("missing", "missing", "third"), "Choose one or two commanders"),
        new SelectionCase(List.of("missing", "missing"), "Commanders must be distinct"),
        new SelectionCase(List.of("missing"), "Commander must be in the deck"),
        new SelectionCase(List.of("Leader", "missing"), "Commander must be in the deck"),
        new SelectionCase(List.of("missing", "Leader"), "Commander must be in the deck"));
  }

  @ParameterizedTest
  @MethodSource("invalidSelections")
  void selectionErrorsRetainPrecedenceAndMessages(SelectionCase scenario) {
    assertThatThrownBy(() -> warnings(List.of(leader()), scenario.ids()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(scenario.message());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 251})
  void invalidDeckSizePrecedesSelectionErrors(int size) {
    assertThatThrownBy(() -> warnings(Collections.nCopies(size, basic()), null))
        .hasMessage("Deck must contain 1–250 cards");
  }

  @Test
  void nullDeckIsRejectedBeforeSelection() {
    assertThatThrownBy(() -> warnings(null, null)).hasMessage("Deck must contain 1–250 cards");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 250})
  void validDeckBoundsPreserveSizeObservation(int size) {
    List<Definition> deck = new ArrayList<>(Collections.nCopies(size - 1, basic()));
    deck.addFirst(leader());
    assertThat(warnings(deck, List.of("Leader")))
        .containsExactlyElementsOf(size == 100 ? List.of() : List.of(sizeWarning(size)));
  }

  @Test
  void overlappingWarningsRetainOrderAndStableDeduplication() {
    Definition red = card("Red", "Artifact", "", true, "R");
    Definition blue = card("Blue", "Legendary Enchantment", "", true, "U");
    Definition bad = card("Bad", "Creature", "", false, "G");
    assertThat(warnings(List.of(red, blue, bad, bad, bad), List.of("Blue", "Red")))
        .containsExactly(
            sizeWarning(5),
            "Check commander eligibility for Blue.",
            "Check commander eligibility for Red.",
            "Check that the chosen commanders can be paired.",
            "Bad is not listed as Commander legal.",
            "Bad exceeds the chosen color identity.",
            "Check singleton exception for Bad.");
  }

  @Test
  void twoCommandersCombineIdentityAndAllowColorlessCards() {
    Definition blue = card("Blue", "Legendary Creature", "", true, "U");
    Definition mixed = card("Mixed", "Creature", "", true, "R", "U");
    Definition colorless = card("Colorless", "Artifact", "", true);
    assertThat(warnings(List.of(leader(), blue, mixed, colorless), List.of("Leader", "Blue")))
        .containsExactly(sizeWarning(4), "Check that the chosen commanders can be paired.");
  }

  @Test
  void duplicateSelectedIdUsesFirstMatchingDefinitionForIdentityAndEligibility() {
    Definition first = leader();
    Definition later =
        new Definition("Leader", "Later", "", "", "Basic Land", List.of("U"), true, List.of());
    assertThat(warnings(List.of(first, later), List.of("Leader")))
        .containsExactly(sizeWarning(2), "Later exceeds the chosen color identity.");
  }

  @ParameterizedTest
  @CsvSource({
    "Basic Land,'',false",
    "NotBasic Creature,'',false",
    "basic Land,'',true",
    "Creature,any number of cards named Repeated,false",
    "Creature,Any number of cards named Repeated,true",
    "Creature,'',true"
  })
  void copyExceptionsRetainCaseSensitiveSubstringSemantics(String type, String text, boolean warn) {
    Definition repeated = card("Repeated", type, text, true, "R");
    List<String> expected = new ArrayList<>(List.of(sizeWarning(3)));
    if (warn) expected.add("Check singleton exception for Repeated.");
    assertThat(warnings(List.of(leader(), repeated, repeated), List.of("Leader")))
        .containsExactlyElementsOf(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "Legendary Creature,false", "Legendary Artifact,true", "Creature,true",
    "legendary Creature,true", "Legendary creature,true", "NotLegendary Creature,false"
  })
  void eligibilityRetainsCaseSensitiveSubstringSemantics(String type, boolean warn) {
    Definition selected = card("Selected", type, "", true);
    List<String> expected = new ArrayList<>(List.of(sizeWarning(1)));
    if (warn) expected.add("Check commander eligibility for Selected.");
    assertThat(warnings(List.of(selected), List.of("Selected")))
        .containsExactlyElementsOf(expected);
  }

  @Test
  void validationDoesNotMutateDeckOrSelectionOrder() {
    List<Definition> deck = new ArrayList<>(List.of(basic(), leader()));
    List<String> selected = new ArrayList<>(List.of("Leader"));
    warnings(deck, selected);
    assertThat(deck).containsExactly(basic(), leader());
    assertThat(selected).containsExactly("Leader");
  }

  @Test
  void standardRetainsFreshLegalBasicDeckAndCommanderRejection() {
    Definition land =
        new Definition(
            "Mountain",
            "Mountain",
            "",
            "",
            "Basic Land",
            List.of("R"),
            false,
            List.of(),
            Map.of("standard", "legal"),
            CLOCK.millis());
    List<Definition> deck = Collections.nCopies(60, land);
    assertThat(DeckRules.validate(GameFormat.STANDARD, deck, null, CLOCK)).isEmpty();
    assertThatThrownBy(
            () -> DeckRules.validate(GameFormat.STANDARD, deck, List.of("Mountain"), CLOCK))
        .hasMessage("Standard does not use commanders");
  }

  private static String sizeWarning(int size) {
    return "Deck contains " + size + " cards; Commander normally uses 100 including commanders.";
  }
}
