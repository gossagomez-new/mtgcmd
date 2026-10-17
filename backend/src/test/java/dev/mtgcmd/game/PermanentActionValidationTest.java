package dev.mtgcmd.game;

import static dev.mtgcmd.game.GameZones.*;
import static dev.mtgcmd.game.PermanentActionFixture.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class PermanentActionValidationTest {
  private static final String MOVE = "MOVE";
  private static final String TAP = "TAP";
  private static final String CONTROL = "CONTROL";
  private static final String POSITION = "POSITION";
  private static final String REVEAL = "REVEAL";
  private static final String FACE_DOWN_COMMAND = "FACE_DOWN";

  static Stream<Arguments> battlefieldCommands() {
    return Arrays.stream(GameFormat.values())
        .flatMap(
            format ->
                List.of(TAP, CONTROL, "ATTACH", POSITION, "COMBAT").stream()
                    .map(command -> Arguments.of(format, command)));
  }

  @ParameterizedTest
  @MethodSource("battlefieldCommands")
  void battlefieldValidationPrecedesCommandSpecificPayloadParsing(
      final GameFormat format, final String command) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, HAND);
    fixture.reject(0, command, Map.of(CARD_ID, card.id), BATTLEFIELD_ERROR);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void movementRejectsUnknownZonesAndUndesignatedCommanders(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    fixture.reject(0, MOVE, Map.of(CARD_ID, card.id, ZONE, "unknown"), "Unknown zone");
    fixture.reject(
        0,
        MOVE,
        Map.of(CARD_ID, card.id, ZONE, COMMAND),
        "Only designated commanders enter the command zone");
    assertThat(card.zone).isEqualTo(BATTLEFIELD);
    if (format.usesCommanders()) {
      card.commanderKey = "commander";
      fixture.apply(0, MOVE, Map.of(CARD_ID, card.id, ZONE, COMMAND));
      assertThat(card.zone).isEqualTo(COMMAND);
      assertThat(card.commanderKey).isEqualTo("commander");
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void privateZonesRejectFaceDownEvenWithLibrarySearchPermission(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    fixture.game.searches.put(0, 0);
    for (final String zone : List.of(HAND, LIBRARY)) {
      final Game.Card card = fixture.card(0, zone);
      fixture.reject(
          0, FACE_DOWN_COMMAND, Map.of(CARD_ID, card.id), "Face-down applies to public objects");
      assertThat(card.faceDown).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void cardPermissionPrecedesDestinationParsingAndOtherValidation(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(1, BATTLEFIELD);
    fixture.reject(0, MOVE, Map.of(CARD_ID, card.id), PERMISSION_ERROR);
    fixture.reject(0, POSITION, Map.of(CARD_ID, card.id), PERMISSION_ERROR);
    fixture.reject(0, TAP, Map.of(CARD_ID, "missing"), "Card unavailable");
    fixture.game.players.getFirst().conceded = true;
    fixture.reject(0, TAP, Map.of(CARD_ID, card.id), "You have conceded");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void controlRejectsMissingOutOfBoundsAndConcededSeats(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    fixture.reject(0, CONTROL, Map.of(CARD_ID, card.id), "Invalid target");
    fixture.reject(
        0, CONTROL, Map.of(CARD_ID, card.id, TARGET, format.seatCapacity()), "Invalid target");
    fixture.game.players.get(1).conceded = true;
    fixture.reject(0, CONTROL, Map.of(CARD_ID, card.id, TARGET, 1), "Target player conceded");
    assertThat(card.controller).isZero();
    assertThat(card.zoneOwner).isZero();
    fixture.game.players.get(1).conceded = false;
    fixture.apply(0, CONTROL, Map.of(CARD_ID, card.id, TARGET, format.seatCapacity() - 1));
    assertThat(card.controller).isEqualTo(format.seatCapacity() - 1);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void invalidFlagsAreRejectedWithoutChangingTheirState(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    fixture.reject(0, TAP, Map.of(CARD_ID, card.id, "tapped", "true"), "Invalid tapped");
    fixture.reject(
        0, FACE_DOWN_COMMAND, Map.of(CARD_ID, card.id, FACE_DOWN, 1), "Invalid faceDown");
    fixture.reject(0, MOVE, Map.of(CARD_ID, card.id, ZONE, HAND, "top", 1), "Invalid top");
    assertThat(card.tapped).isFalse();
    assertThat(card.faceDown).isFalse();
    assertThat(card.zone).isEqualTo(BATTLEFIELD);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void invalidRevealAudiencesRetainPreviousPermissions(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    card.revealedTo.add(1);
    fixture.reject(0, REVEAL, Map.of(CARD_ID, card.id), "Reveal requires an audience");
    fixture.reject(
        0, REVEAL, Map.of(CARD_ID, card.id, AUDIENCE, "all"), "Reveal requires an audience");
    fixture.reject(0, REVEAL, Map.of(CARD_ID, card.id, AUDIENCE, List.of("1")), "Invalid audience");
    for (final Number seat :
        List.of(-1, format.seatCapacity(), 0.5, Double.NaN, Double.POSITIVE_INFINITY)) {
      fixture.reject(
          0, REVEAL, Map.of(CARD_ID, card.id, AUDIENCE, List.of(0, seat)), "Invalid audience");
      assertThat(card.revealedTo).containsExactly(1);
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void invalidCoordinatesRejectBeforeEitherPositionIsAssigned(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    card.x = 0.4;
    card.y = 0.6;
    for (final Object value : List.of(-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY, "0.5")) {
      fixture.reject(0, POSITION, Map.of(CARD_ID, card.id, "x", value, "y", 0.3), "Invalid x");
      fixture.reject(0, POSITION, Map.of(CARD_ID, card.id, "x", 0.3, "y", value), "Invalid y");
      assertThat(card.x).isEqualTo(0.4);
      assertThat(card.y).isEqualTo(0.6);
    }
    fixture.reject(0, POSITION, Map.of(CARD_ID, card.id), "Invalid x");
    fixture.reject(0, POSITION, Map.of(CARD_ID, card.id, "x", 0.3), "Invalid y");
  }
}
