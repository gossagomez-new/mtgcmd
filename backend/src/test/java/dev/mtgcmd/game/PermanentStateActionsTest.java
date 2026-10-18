package dev.mtgcmd.game;

import static dev.mtgcmd.game.GameZones.*;
import static dev.mtgcmd.game.PermanentActionFixture.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PermanentStateActionsTest {
  private static final String MOVE = "MOVE";
  private static final String TAP = "TAP";
  private static final String TRANSFORM = "TRANSFORM";

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void movingAssignsTheSuccessfulRevisionAsStackOrderAndForwardsFlags(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, HAND);
    fixture.apply(0, MOVE, Map.of(CARD_ID, card.id, ZONE, STACK, FACE_DOWN, true));
    assertThat(card.zone).isEqualTo(STACK);
    assertThat(card.faceDown).isTrue();
    assertThat(card.stackOrder).isEqualTo(fixture.game.revision);
    fixture.apply(0, MOVE, Map.of(CARD_ID, card.id, ZONE, BATTLEFIELD));
    assertThat(card.zone).isEqualTo(BATTLEFIELD);
    assertThat(card.faceDown).isFalse();
    assertThat(card.stackOrder).isEqualTo(fixture.game.revision);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void movingToTheLibraryHonorsTopAndDefaultBottomPlacement(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card first = fixture.card(0, LIBRARY);
    final Game.Card incoming = fixture.card(0, HAND);
    fixture.apply(0, MOVE, Map.of(CARD_ID, incoming.id, ZONE, LIBRARY, "top", true));
    assertThat(GameLookup.zone(fixture.game, 0, LIBRARY)).containsExactly(incoming, first);
    fixture.game.searches.put(0, 0);
    fixture.apply(0, MOVE, Map.of(CARD_ID, incoming.id, ZONE, LIBRARY));
    assertThat(GameLookup.zone(fixture.game, 0, LIBRARY)).containsExactly(first, incoming);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void tappingTogglesByDefaultAndExplicitValuesAreIdempotent(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    fixture.apply(0, TAP, Map.of(CARD_ID, card.id));
    assertThat(card.tapped).isTrue();
    fixture.apply(0, TAP, Map.of(CARD_ID, card.id));
    assertThat(card.tapped).isFalse();
    fixture.apply(0, TAP, Map.of(CARD_ID, card.id, "tapped", true));
    fixture.apply(0, TAP, Map.of(CARD_ID, card.id, "tapped", true));
    assertThat(card.tapped).isTrue();
    fixture.apply(0, TAP, Map.of(CARD_ID, card.id, "tapped", false));
    assertThat(card.tapped).isFalse();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void untapAllSelectsOnlyBattlefieldCardsControlledByTheActor(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card own = fixture.card(0, BATTLEFIELD);
    final Game.Card stolen = fixture.card(1, BATTLEFIELD);
    stolen.controller = 0;
    stolen.zoneOwner = 0;
    final Game.Card transferred = fixture.card(0, BATTLEFIELD);
    transferred.controller = 1;
    transferred.zoneOwner = 1;
    final Game.Card opponent = fixture.card(1, BATTLEFIELD);
    final Game.Card hand = fixture.card(0, HAND);
    final Game.Card stack = fixture.card(0, STACK);
    for (final Game.Card card : fixture.game.cards) {
      card.tapped = true;
    }
    fixture.apply(0, "UNTAP_ALL", Map.of());
    assertThat(own.tapped).isFalse();
    assertThat(stolen.tapped).isFalse();
    for (final Game.Card card : List.of(transferred, opponent, hand, stack)) {
      assertThat(card.tapped).isTrue();
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void transformationAdvancesAndWrapsAndRejectsZeroOrSingleFaceCards(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    for (final int faces : List.of(0, 1)) {
      card.definition = definition(faces);
      fixture.reject(0, TRANSFORM, Map.of(CARD_ID, card.id), "Card has no other face");
      assertThat(card.face).isZero();
    }
    card.definition = definition(2);
    fixture.apply(0, TRANSFORM, Map.of(CARD_ID, card.id));
    assertThat(card.face).isEqualTo(1);
    fixture.apply(0, TRANSFORM, Map.of(CARD_ID, card.id));
    assertThat(card.face).isZero();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void controlTransfersZoneOwnershipAndCombatButNotOwnership(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    card.combat = ATTACKER;
    fixture.apply(0, "CONTROL", Map.of(CARD_ID, card.id, TARGET, 1));
    assertThat(card.owner).isZero();
    assertThat(card.controller).isEqualTo(1);
    assertThat(card.zoneOwner).isEqualTo(1);
    assertThat(card.combat).isEmpty();
    fixture.reject(0, TAP, Map.of(CARD_ID, card.id), PERMISSION_ERROR);
    fixture.apply(1, TAP, Map.of(CARD_ID, card.id));
    assertThat(card.tapped).isTrue();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void positioningStoresInteriorAndEndpointCoordinates(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    for (final double horizontal : List.of(0.0, 0.25, 1.0)) {
      final double vertical = 1 - horizontal;
      fixture.apply(0, "POSITION", Map.of(CARD_ID, card.id, "x", horizontal, "y", vertical));
      assertThat(card.x).isEqualTo(horizontal);
      assertThat(card.y).isEqualTo(vertical);
    }
  }
}
