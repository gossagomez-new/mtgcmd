package dev.mtgcmd.game;

import static dev.mtgcmd.game.LifecycleFixture.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LifecycleTurnsTest {
  private static final String ATTACKER = "attacker";
  private static final List<String> EXPECTED_PHASES =
      List.of(
          "Upkeep",
          "Draw",
          "Main 1",
          "Begin combat",
          COMBAT,
          "Blockers",
          "Damage",
          "End combat",
          "Main 2",
          "End step",
          "Cleanup");

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void phasesAdvanceInOrderAndCleanupStartsTheNextTurnAndClearsCombat(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    final Game.Card card = fixture.game.cards.getFirst();
    card.combat = ATTACKER;
    final int turn = fixture.game.turn;
    for (final String expected : EXPECTED_PHASES) {
      fixture.apply(0, PHASE, Map.of());
      assertThat(fixture.game.phase).isEqualTo(expected);
      assertThat(fixture.game.turn).isEqualTo(turn);
      assertThat(fixture.game.active).isZero();
      assertThat(card.combat).isEqualTo(ATTACKER);
    }
    fixture.apply(0, PHASE, Map.of());
    assertThat(fixture.game.phase).isEqualTo(UNTAP);
    assertThat(fixture.game.turn).isEqualTo(turn + 1);
    assertThat(fixture.game.active).isEqualTo(1);
    assertThat(fixture.game.cards).allSatisfy(permanent -> assertThat(permanent.combat).isEmpty());
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void onlyTheHostOrActiveSeatCanAdvancePhases(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    fixture.reject(1, PHASE, Map.of(), "Only the active player or host advances phases");
    fixture.game.active = 1;
    fixture.apply(1, PHASE, Map.of());
    assertThat(fixture.game.phase).isEqualTo("Upkeep");
    fixture.apply(0, PHASE, Map.of());
    assertThat(fixture.game.phase).isEqualTo("Draw");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void cleanupWrapsAndSkipsConcededSeatsIncludingWhenOnlyTheActorRemains(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    fixture.game.active = format.seatCapacity() - 1;
    fixture.game.phase = "Cleanup";
    fixture.apply(fixture.game.active, PHASE, Map.of());
    assertThat(fixture.game.active).isZero();
    for (int seat = 1; seat < format.seatCapacity(); seat++) {
      fixture.game.players.get(seat).conceded = true;
    }
    fixture.game.phase = "Cleanup";
    final int turn = fixture.game.turn;
    fixture.apply(0, PHASE, Map.of());
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.game.turn).isEqualTo(turn + 1);
    assertThat(fixture.game.phase).isEqualTo(UNTAP);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void activeConcessionTransfersTheTurnAndClearsCombat(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    fixture.game.phase = COMBAT;
    final Game.Card card = fixture.game.cards.getFirst();
    card.combat = ATTACKER;
    final int turn = fixture.game.turn;
    if (format == GameFormat.COMMANDER) {
      fixture.game.players.get(1).conceded = true;
    }
    fixture.apply(0, CONCEDE, Map.of());
    assertThat(fixture.game.players.getFirst().conceded).isTrue();
    assertThat(fixture.game.active).isEqualTo(format == GameFormat.COMMANDER ? 2 : 1);
    assertThat(fixture.game.turn).isEqualTo(turn + 1);
    assertThat(fixture.game.phase).isEqualTo(UNTAP);
    assertThat(card.combat).isEmpty();
    fixture.reject(0, PHASE, Map.of(), "You have conceded");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void nonactiveConcessionPreservesTheCurrentTurnPhaseAndCombat(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    fixture.game.phase = COMBAT;
    final Game.Card card = fixture.game.cards.getFirst();
    card.combat = ATTACKER;
    final int turn = fixture.game.turn;
    fixture.apply(1, CONCEDE, Map.of());
    assertThat(fixture.game.players.get(1).conceded).isTrue();
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.game.turn).isEqualTo(turn);
    assertThat(fixture.game.phase).isEqualTo(COMBAT);
    assertThat(card.combat).isEqualTo(ATTACKER);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void theLastPlayerCanConcedeWithoutAdvancingOrAutomaticallyFinishing(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    for (int seat = 1; seat < format.seatCapacity(); seat++) {
      fixture.game.players.get(seat).conceded = true;
    }
    fixture.game.phase = COMBAT;
    final int turn = fixture.game.turn;
    fixture.apply(0, CONCEDE, Map.of());
    assertThat(fixture.game.players).allSatisfy(player -> assertThat(player.conceded).isTrue());
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.game.turn).isEqualTo(turn);
    assertThat(fixture.game.phase).isEqualTo(COMBAT);
    assertThat(fixture.game.status).isEqualTo("playing");
    fixture.apply(0, FINISH, Map.of());
    assertThat(fixture.game.status).isEqualTo("finished");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void activeConcessionWrapsFromTheLastSeatToTheFirst(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    fixture.game.active = format.seatCapacity() - 1;
    fixture.apply(fixture.game.active, CONCEDE, Map.of());
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.game.phase).isEqualTo(UNTAP);
  }
}
