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
  private static final String BLUE_CARD_NAME = "Blue";

  private static final String LEADER_NAME = "Leader";

  private static final String MOUNTAIN_NAME = "Mountain";

  private static final String MISSING_ID = "missing";

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC);

  private static Definition card(
      final String identifier,
      final String type,
      final String text,
      final boolean legal,
      final String... colors) {
    return new Definition(
        identifier, identifier, "", text, type, List.of(colors), legal, List.of());
  }

  private static Definition leader() {
    return card(LEADER_NAME, "Legendary Creature", "", true, "R");
  }

  private static Definition basic() {
    return card(MOUNTAIN_NAME, "Basic Land — Mountain", "", true, "R");
  }

  private static List<String> warnings(final List<Definition> deck, final List<String> commanders) {
    return DeckRules.validate(GameFormat.COMMANDER, deck, commanders, CLOCK);
  }

  record SelectionCase(List<String> ids, String message) {}

  static Stream<SelectionCase> invalidSelections() {
    return Stream.of(
        new SelectionCase(null, "Choose one or two commanders"),
        new SelectionCase(List.of(), "Choose one or two commanders"),
        new SelectionCase(List.of(MISSING_ID, MISSING_ID, "third"), "Choose one or two commanders"),
        new SelectionCase(List.of(MISSING_ID, MISSING_ID), "Commanders must be distinct"),
        new SelectionCase(List.of(MISSING_ID), "Commander must be in the deck"),
        new SelectionCase(List.of(LEADER_NAME, MISSING_ID), "Commander must be in the deck"),
        new SelectionCase(List.of(MISSING_ID, LEADER_NAME), "Commander must be in the deck"));
  }

  @ParameterizedTest
  @MethodSource("invalidSelections")
  void selectionErrorsRetainPrecedenceAndMessages(final SelectionCase scenario) {
    assertThatThrownBy(() -> warnings(List.of(leader()), scenario.ids()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(scenario.message());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 251})
  void invalidDeckSizePrecedesSelectionErrors(final int size) {
    assertThatThrownBy(() -> warnings(Collections.nCopies(size, basic()), null))
        .hasMessage("Deck must contain 1–250 cards");
  }

  @Test
  void nullDeckIsRejectedBeforeSelection() {
    assertThatThrownBy(() -> warnings(null, null)).hasMessage("Deck must contain 1–250 cards");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 250})
  void validDeckBoundsPreserveSizeObservation(final int size) {
    final List<Definition> deck = new ArrayList<>(Collections.nCopies(size - 1, basic()));
    deck.addFirst(leader());
    assertThat(warnings(deck, List.of(LEADER_NAME)))
        .containsExactlyElementsOf(size == 100 ? List.of() : List.of(sizeWarning(size)));
  }

  @Test
  void overlappingWarningsRetainOrderAndStableDeduplication() {
    final Definition red = card("Red", "Artifact", "", true, "R");
    final Definition blue = card(BLUE_CARD_NAME, "Legendary Enchantment", "", true, "U");
    final Definition bad = card("Bad", "Creature", "", false, "G");
    assertThat(warnings(List.of(red, blue, bad, bad, bad), List.of(BLUE_CARD_NAME, "Red")))
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
    final Definition blue = card(BLUE_CARD_NAME, "Legendary Creature", "", true, "U");
    final Definition mixed = card("Mixed", "Creature", "", true, "R", "U");
    final Definition colorless = card("Colorless", "Artifact", "", true);
    assertThat(
            warnings(
                List.of(leader(), blue, mixed, colorless), List.of(LEADER_NAME, BLUE_CARD_NAME)))
        .containsExactly(sizeWarning(4), "Check that the chosen commanders can be paired.");
  }

  @Test
  void duplicateSelectedIdUsesFirstMatchingDefinitionForIdentityAndEligibility() {
    final Definition first = leader();
    final Definition later =
        new Definition(LEADER_NAME, "Later", "", "", "Basic Land", List.of("U"), true, List.of());
    assertThat(warnings(List.of(first, later), List.of(LEADER_NAME)))
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
  void copyExceptionsRetainCaseSensitiveSubstringSemantics(
      final String type, final String text, final boolean warn) {
    final Definition repeated = card("Repeated", type, text, true, "R");
    final List<String> expected = new ArrayList<>(List.of(sizeWarning(3)));
    if (warn) {
      expected.add("Check singleton exception for Repeated.");
    }
    assertThat(warnings(List.of(leader(), repeated, repeated), List.of(LEADER_NAME)))
        .containsExactlyElementsOf(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "Legendary Creature,false", "Legendary Artifact,true", "Creature,true",
    "legendary Creature,true", "Legendary creature,true", "NotLegendary Creature,false"
  })
  void eligibilityRetainsCaseSensitiveSubstringSemantics(final String type, final boolean warn) {
    final Definition selected = card("Selected", type, "", true);
    final List<String> expected = new ArrayList<>(List.of(sizeWarning(1)));
    if (warn) {
      expected.add("Check commander eligibility for Selected.");
    }
    assertThat(warnings(List.of(selected), List.of("Selected")))
        .containsExactlyElementsOf(expected);
  }

  @Test
  void validationDoesNotMutateDeckOrSelectionOrder() {
    final List<Definition> deck = new ArrayList<>(List.of(basic(), leader()));
    final List<String> selected = new ArrayList<>(List.of(LEADER_NAME));
    warnings(deck, selected);
    assertThat(deck).containsExactly(basic(), leader());
    assertThat(selected).containsExactly(LEADER_NAME);
  }

  @Test
  void standardRetainsFreshLegalBasicDeckAndCommanderRejection() {
    final Definition land =
        new Definition(
            MOUNTAIN_NAME,
            MOUNTAIN_NAME,
            "",
            "",
            "Basic Land",
            List.of("R"),
            false,
            List.of(),
            Map.of("standard", "legal"),
            CLOCK.millis());
    final List<Definition> deck = Collections.nCopies(60, land);
    assertThat(DeckRules.validate(GameFormat.STANDARD, deck, null, CLOCK)).isEmpty();
    assertThatThrownBy(
            () -> DeckRules.validate(GameFormat.STANDARD, deck, List.of(MOUNTAIN_NAME), CLOCK))
        .hasMessage("Standard does not use commanders");
  }

  private static String sizeWarning(final int size) {
    return "Deck contains " + size + " cards; Commander normally uses 100 including commanders.";
  }
}
