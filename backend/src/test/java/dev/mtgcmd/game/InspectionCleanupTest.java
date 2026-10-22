package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InspectionCleanupTest {
  private static final String INSPECTION_FIELD = "libraryInspection";
  private static final String SCRY = "SCRY";
  private static final String LOOK = "LOOK_TOP";
  private static final String GRANT = "GRANT";
  private static final String SEARCH = "SEARCH";
  private static final String CLOSE = "CLOSE_INSPECTION";
  private static final String APPLY = "APPLY_SCRY";
  private static final String MOVE = "MOVE";
  private static final String TARGET = "target";
  private static final String CARD_ID = "cardId";
  private static final String INSPECTION_ID = "inspectionId";
  private static final String ZONE = "zone";
  private static final String LIBRARY = "library";
  private static final String HAND = "hand";

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void delegatedLibraryMutationsRemoveTargetSessionsAndPreserveTheActorsOtherInspection(
      final GameFormat format) throws JsonProcessingException {
    for (final String operation : List.of("DRAW", "SHUFFLE", "EXILE_TOP", "MOVE_IN", APPLY)) {
      final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
      fixture.table.apply(0, GRANT, Map.of(TARGET, 1));
      fixture.open(0, 0, SCRY, 2);
      fixture.open(1, 1, LOOK, 2);
      if (format.seatCapacity() > 2) {
        fixture.table.apply(0, GRANT, Map.of(TARGET, 2));
        fixture.open(2, 0, LOOK, 2);
      }
      final Game.LibraryInspection unrelated = fixture.game.libraryInspections.get(1);
      if ("MOVE_IN".equals(operation)) {
        fixture.table.apply(
            1,
            MOVE,
            Map.of(CARD_ID, GameLookup.zone(fixture.game, 0, HAND).getFirst().id, ZONE, LIBRARY));
      } else if (APPLY.equals(operation)) {
        final Game.LibraryInspection session = fixture.game.libraryInspections.get(0);
        fixture.commit(
            0,
            session.id(),
            List.of(session.cardIds().get(1)),
            List.of(session.cardIds().getFirst()));
      } else {
        fixture.table.apply(1, operation, Map.of(TARGET, 0));
      }
      assertThat(fixture.game.libraryInspections).containsOnlyKeys(1).containsEntry(1, unrelated);
      assertThat(fixture.view(0).path(INSPECTION_FIELD).isNull()).isTrue();
      assertThat(fixture.view(1).path(INSPECTION_FIELD).path("id").asText())
          .isEqualTo(unrelated.id());
      assertRoundTrip(fixture.game);
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void movingOutOfALibraryRemovesDelegatesWithoutDependingOnTheOwnersSearchReplacement(
      final GameFormat format) throws JsonProcessingException {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    fixture.table.apply(0, GRANT, Map.of(TARGET, 1));
    fixture.table.apply(0, SEARCH, Map.of());
    fixture.open(1, 0, LOOK, 2);
    final Map<Integer, Game.LibraryInspection> retained = new LinkedHashMap<>();
    if (format.seatCapacity() > 2) {
      fixture.open(2, 2, SCRY, 2);
      retained.put(2, fixture.game.libraryInspections.get(2));
    }
    fixture.table.apply(0, MOVE, Map.of(CARD_ID, fixture.ids(0).getLast(), ZONE, HAND));
    assertThat(fixture.game.libraryInspections).isEqualTo(retained);
    assertThat(fixture.view(1).path(INSPECTION_FIELD).isNull()).isTrue();
    assertRoundTrip(fixture.game);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void revocationDeletesOnlyTheDelegatesMatchingTargetAndDoesNotResurrectOnRegrant(
      final GameFormat format) throws JsonProcessingException {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    fixture.table.apply(0, GRANT, Map.of(TARGET, 1));
    fixture.open(0, 0, SCRY, 2);
    fixture.open(1, 0, LOOK, 2);
    final Game.LibraryInspection owner = fixture.game.libraryInspections.get(0);
    fixture.table.apply(0, GRANT, Map.of(TARGET, 1, "allow", false));
    assertThat(fixture.game.libraryInspections).containsOnlyKeys(0).containsEntry(0, owner);
    assertThat(fixture.view(1).path(INSPECTION_FIELD).isNull()).isTrue();
    fixture.table.apply(0, GRANT, Map.of(TARGET, 1));
    assertThat(fixture.game.libraryInspections).doesNotContainKey(1);
    fixture.open(1, 1, LOOK, 1);
    final Game.LibraryInspection unrelated = fixture.game.libraryInspections.get(1);
    fixture.table.apply(0, GRANT, Map.of(TARGET, 1, "allow", false));
    assertThat(fixture.game.libraryInspections).containsEntry(0, owner).containsEntry(1, unrelated);
    assertRoundTrip(fixture.game);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void concessionRemovesTheActorsOutgoingAndOtherPlayersIncomingInspections(final GameFormat format)
      throws JsonProcessingException {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    fixture.table.apply(0, GRANT, Map.of(TARGET, 1));
    fixture.table.apply(1, GRANT, Map.of(TARGET, 0));
    fixture.open(0, 1, SCRY, 2);
    fixture.open(1, 0, LOOK, 2);
    final Map<Integer, Game.LibraryInspection> retained = new LinkedHashMap<>();
    if (format.seatCapacity() > 2) {
      fixture.open(2, 2, LOOK, 1);
      retained.put(2, fixture.game.libraryInspections.get(2));
    }
    fixture.table.apply(0, "CONCEDE", Map.of());
    assertThat(fixture.game.libraryInspections).isEqualTo(retained);
    assertThat(fixture.view(0).path(INSPECTION_FIELD).isNull()).isTrue();
    assertThat(fixture.view(1).path(INSPECTION_FIELD).isNull()).isTrue();
    assertRoundTrip(fixture.game);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void finishClearsAllStoredSessionsNotJustTheirProjection(final GameFormat format)
      throws JsonProcessingException {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    for (int seat = 0; seat < format.seatCapacity(); seat++) {
      fixture.open(seat, seat, LOOK, 1);
    }
    fixture.reject(1, "FINISH", Map.of());
    fixture.table.apply(0, "FINISH", Map.of());
    assertThat(fixture.game.libraryInspections).isEmpty();
    assertRoundTrip(fixture.game);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void explicitCloseAndSearchReplaceOnlyTheActorsSession(final GameFormat format)
      throws JsonProcessingException {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    fixture.open(0, 0, LOOK, 2);
    fixture.open(1, 1, SCRY, 2);
    final Game.LibraryInspection unrelated = fixture.game.libraryInspections.get(1);
    final String first = fixture.game.libraryInspections.get(0).id();
    fixture.reject(0, CLOSE, Map.of(INSPECTION_ID, "stale"));
    fixture.table.apply(0, CLOSE, Map.of(INSPECTION_ID, first));
    assertThat(fixture.game.libraryInspections).containsOnlyKeys(1).containsEntry(1, unrelated);
    fixture.reject(0, CLOSE, Map.of(INSPECTION_ID, first));
    fixture.open(0, 0, SCRY, 1);
    fixture.table.apply(0, SEARCH, Map.of());
    assertThat(fixture.game.libraryInspections).containsOnlyKeys(1).containsEntry(1, unrelated);
    fixture.open(0, 0, LOOK, 1);
    assertThat(fixture.game.searches).doesNotContainKey(0);
    assertThat(fixture.game.libraryInspections).containsEntry(1, unrelated).containsKey(0);
    assertRoundTrip(fixture.game);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void rejectedMutationsAndUnrelatedActionsPreserveStoredSessionsAndWholeState(
      final GameFormat format) throws JsonProcessingException {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final String id = fixture.open(0, 0, SCRY, 2);
    fixture.open(1, 1, LOOK, 1);
    final Map<Integer, Game.LibraryInspection> before =
        new LinkedHashMap<>(fixture.game.libraryInspections);
    for (final String command : List.of("DRAW", "SHUFFLE", "EXILE_TOP", LOOK, SCRY)) {
      fixture.reject(1, command, Map.of(TARGET, 0));
      fixture.reject(0, command, Map.of("count", -1, TARGET, 99));
    }
    fixture.reject(0, MOVE, Map.of(CARD_ID, fixture.ids(0).getFirst(), ZONE, HAND));
    fixture.reject(0, APPLY, Map.of(INSPECTION_ID, id, "top", List.of(), "bottom", List.of()));
    final JsonNode state = BoundedLibraryFixture.MAPPER.valueToTree(fixture.game);
    assertThatThrownBy(
            () ->
                fixture.table.engine.apply(
                    fixture.game,
                    0,
                    new Game.Command(
                        UUID.randomUUID().toString(),
                        fixture.game.revision - 1,
                        "SHUFFLE",
                        Map.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Stale revision");
    assertThat(BoundedLibraryFixture.MAPPER.<JsonNode>valueToTree(fixture.game)).isEqualTo(state);
    fixture.table.apply(1, "COUNTER", Map.of("name", "life", "delta", -1));
    assertThat(fixture.game.libraryInspections).isEqualTo(before);
    assertRoundTrip(fixture.game);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void cleanupIsSafeForMissingMapsNullEntriesAndRepeatedCalls(final GameFormat format)
      throws JsonProcessingException {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    fixture.game.libraryInspections = null;
    LibraryInspections.close(fixture.game, 0);
    LibraryInspections.invalidate(fixture.game, 0);
    LibraryInspections.revoke(fixture.game, 0, 1);
    LibraryInspections.clear(fixture.game);
    assertThat(fixture.game.libraryInspections).isNull();
    fixture.open(1, 1, LOOK, 1);
    final Game.LibraryInspection other = fixture.game.libraryInspections.get(1);
    fixture.game.libraryInspections.put(0, null);
    LibraryInspections.invalidate(fixture.game, 0);
    LibraryInspections.invalidate(fixture.game, 0);
    assertThat(fixture.game.libraryInspections).containsOnlyKeys(1).containsEntry(1, other);
    LibraryInspections.close(fixture.game, 1);
    LibraryInspections.close(fixture.game, 1);
    LibraryInspections.revoke(fixture.game, 1, 1);
    LibraryInspections.clear(fixture.game);
    LibraryInspections.clear(fixture.game);
    assertThat(fixture.game.libraryInspections).isEmpty();
    assertRoundTrip(fixture.game);
  }

  private static void assertRoundTrip(final Game game) throws JsonProcessingException {
    final Game recovered =
        BoundedLibraryFixture.MAPPER.readValue(
            BoundedLibraryFixture.MAPPER.writeValueAsString(game), Game.class);
    assertThat(recovered.libraryInspections).isEqualTo(game.libraryInspections);
    for (int seat = 0; seat < game.players.size(); seat++) {
      assertThat(new GameEngine().view(recovered, seat))
          .isEqualTo(new GameEngine().view(game, seat));
    }
  }
}
