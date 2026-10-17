package dev.mtgcmd.game;

import static dev.mtgcmd.game.GameZones.*;
import static dev.mtgcmd.game.PermanentActionFixture.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PermanentRelationsTest {
  private static final String ATTACH = "ATTACH";
  private static final String DEFINITION = "definition";
  private static final String COMBAT = "COMBAT";
  private static final String REVEAL = "REVEAL";
  private static final String FACE_DOWN_COMMAND = "FACE_DOWN";
  private static final String CYCLE_ERROR = "Attachment cycle";

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void faceDownClearsPreviousRevealsAndHidesDefinitionFromThePreviousAudience(
      final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    fixture.apply(0, REVEAL, Map.of(CARD_ID, card.id, AUDIENCE, List.of(1)));
    assertThat(card.revealedTo).containsExactly(1);
    assertThat(fixture.viewCard(1, card).has(DEFINITION)).isTrue();
    fixture.apply(0, FACE_DOWN_COMMAND, Map.of(CARD_ID, card.id));
    assertThat(card.faceDown).isTrue();
    assertThat(card.revealedTo).isEmpty();
    assertThat(fixture.viewCard(1, card).has(DEFINITION)).isFalse();
    assertThat(fixture.viewCard(0, card).path(DEFINITION).path("name").asText())
        .isEqualTo(card.definition.name());
    fixture.apply(0, FACE_DOWN_COMMAND, Map.of(CARD_ID, card.id));
    assertThat(card.faceDown).isFalse();
    fixture.apply(0, FACE_DOWN_COMMAND, Map.of(CARD_ID, card.id, FACE_DOWN, true));
    fixture.apply(0, FACE_DOWN_COMMAND, Map.of(CARD_ID, card.id, FACE_DOWN, true));
    assertThat(card.faceDown).isTrue();
    fixture.apply(0, FACE_DOWN_COMMAND, Map.of(CARD_ID, card.id, FACE_DOWN, false));
    assertThat(card.faceDown).isFalse();
    assertThat(fixture.viewCard(1, card).has(DEFINITION)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void revealReplacesAndDeduplicatesTheAudienceIncludingTheLastSeat(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    card.faceDown = true;
    card.revealedTo.add(0);
    final int last = format.seatCapacity() - 1;
    fixture.apply(0, REVEAL, Map.of(CARD_ID, card.id, AUDIENCE, List.of(last, last)));
    assertThat(card.revealedTo).containsExactly(last);
    assertThat(fixture.viewCard(last, card).has(DEFINITION)).isTrue();
    fixture.apply(0, REVEAL, Map.of(CARD_ID, card.id, AUDIENCE, List.of()));
    assertThat(card.revealedTo).isEmpty();
    assertThat(fixture.viewCard(last, card).has(DEFINITION)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void attachmentSupportsChainsAndExplicitOrOmittedDetachment(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card source = fixture.card(0, BATTLEFIELD);
    final Game.Card middle = fixture.card(0, BATTLEFIELD);
    final Game.Card target = fixture.card(1, BATTLEFIELD);
    fixture.apply(0, ATTACH, Map.of(CARD_ID, middle.id, TARGET_ID, target.id));
    fixture.apply(0, ATTACH, Map.of(CARD_ID, source.id, TARGET_ID, middle.id));
    assertThat(source.attachedTo).isEqualTo(middle.id);
    assertThat(middle.attachedTo).isEqualTo(target.id);
    fixture.apply(0, ATTACH, Map.of(CARD_ID, source.id, TARGET_ID, ""));
    assertThat(source.attachedTo).isNull();
    fixture.apply(0, ATTACH, Map.of(CARD_ID, source.id, TARGET_ID, middle.id));
    fixture.apply(0, ATTACH, Map.of(CARD_ID, source.id));
    assertThat(source.attachedTo).isNull();
    assertThat(middle.attachedTo).isEqualTo(target.id);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void attachmentsRejectSelfAndIndirectCyclesBeforeAssigningTheTarget(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card source = fixture.card(0, BATTLEFIELD);
    final Game.Card middle = fixture.card(0, BATTLEFIELD);
    final Game.Card target = fixture.card(0, BATTLEFIELD);
    fixture.reject(0, ATTACH, Map.of(CARD_ID, source.id, TARGET_ID, source.id), CYCLE_ERROR);
    assertThat(source.attachedTo).isNull();
    fixture.apply(0, ATTACH, Map.of(CARD_ID, source.id, TARGET_ID, middle.id));
    fixture.apply(0, ATTACH, Map.of(CARD_ID, middle.id, TARGET_ID, target.id));
    fixture.reject(0, ATTACH, Map.of(CARD_ID, target.id, TARGET_ID, source.id), CYCLE_ERROR);
    assertThat(target.attachedTo).isNull();
    assertThat(source.attachedTo).isEqualTo(middle.id);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void attachmentsRejectOffBattlefieldAndMissingTargets(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card source = fixture.card(0, BATTLEFIELD);
    final Game.Card target = fixture.card(1, HAND);
    fixture.reject(0, ATTACH, Map.of(CARD_ID, source.id, TARGET_ID, target.id), BATTLEFIELD_ERROR);
    fixture.reject(0, ATTACH, Map.of(CARD_ID, source.id, TARGET_ID, "missing"), "Card unavailable");
    assertThat(source.attachedTo).isNull();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void combatSupportsAttackersBlockersAndClearingWithoutApplyingCardRules(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card source = fixture.card(0, BATTLEFIELD);
    final Game.Card target = fixture.card(1, BATTLEFIELD);
    fixture.apply(0, COMBAT, Map.of(CARD_ID, source.id, MARKER, ATTACKER));
    assertThat(source.combat).isEqualTo(ATTACKER);
    fixture.apply(0, COMBAT, Map.of(CARD_ID, source.id, MARKER, BLOCKS + target.id));
    assertThat(source.combat).isEqualTo(BLOCKS + target.id);
    fixture.apply(0, COMBAT, Map.of(CARD_ID, source.id, MARKER, ""));
    assertThat(source.combat).isEmpty();
    fixture.apply(0, COMBAT, Map.of(CARD_ID, source.id, MARKER, ATTACKER));
    fixture.apply(0, COMBAT, Map.of(CARD_ID, source.id));
    assertThat(source.combat).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void combatRejectsInvalidMarkersAndUnavailableOrOffBattlefieldBlockTargets(
      final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card source = fixture.card(0, BATTLEFIELD);
    final Game.Card target = fixture.card(1, HAND);
    source.combat = ATTACKER;
    fixture.reject(
        0, COMBAT, Map.of(CARD_ID, source.id, MARKER, "defender"), "Invalid combat marker");
    fixture.reject(
        0, COMBAT, Map.of(CARD_ID, source.id, MARKER, BLOCKS + target.id), BATTLEFIELD_ERROR);
    fixture.reject(
        0, COMBAT, Map.of(CARD_ID, source.id, MARKER, BLOCKS + "missing"), "Card unavailable");
    assertThat(source.combat).isEqualTo(ATTACKER);
  }
}
