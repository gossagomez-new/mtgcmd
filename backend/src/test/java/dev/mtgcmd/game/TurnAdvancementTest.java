package dev.mtgcmd.game;

import static dev.mtgcmd.game.LifecycleFixture.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TurnAdvancementTest {
  private static final String END_TURN = "END_TURN";
  private static final String ACTIVE_ERROR = "Only the active player can advance turns";

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void nonactiveHostCannotAdvanceButTheActivePlayerCan(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    fixture.game.active = 1;
    fixture.reject(0, PHASE, Map.of(), ACTIVE_ERROR);
    fixture.reject(0, END_TURN, Map.of(), ACTIVE_ERROR);
    fixture.apply(1, PHASE, Map.of());
    assertThat(fixture.game.phase).isEqualTo("Upkeep");
    fixture.apply(1, END_TURN, Map.of());
    assertThat(fixture.game.active).isEqualTo(2 % format.seatCapacity());
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void endingAnyPhaseAdvancesOnceWithoutUntappingDrawingOrResolving(final GameFormat format) {
    for (final String phase : GameEngine.PHASES) {
      final LifecycleFixture fixture = new LifecycleFixture(format);
      fixture.startGame();
      fixture.game.phase = phase;
      fixture.game.cards.forEach(
          card -> {
            card.tapped = true;
            card.combat = "attacker";
          });
      final Game.Ability ability = new Game.Ability();
      ability.text = "Unresolved";
      fixture.game.abilities.add(ability);
      final List<String> hand =
          GameLookup.zone(fixture.game, 0, GameZones.HAND).stream().map(card -> card.id).toList();
      final List<String> library =
          GameLookup.zone(fixture.game, 0, GameZones.LIBRARY).stream()
              .map(card -> card.id)
              .toList();
      final int turn = fixture.game.turn;
      fixture.apply(0, END_TURN, Map.of());
      assertThat(fixture.game.active).isEqualTo(1);
      assertThat(fixture.game.turn).isEqualTo(turn + 1);
      assertThat(fixture.game.phase).isEqualTo(UNTAP);
      assertThat(fixture.game.log.getLast()).isEqualTo("Player 0: ended the turn");
      assertThat(fixture.game.cards)
          .allSatisfy(
              card -> {
                assertThat(card.tapped).isTrue();
                assertThat(card.combat).isEmpty();
              });
      assertThat(GameLookup.zone(fixture.game, 0, GameZones.HAND))
          .extracting(card -> card.id)
          .containsExactlyElementsOf(hand);
      assertThat(GameLookup.zone(fixture.game, 0, GameZones.LIBRARY))
          .extracting(card -> card.id)
          .containsExactlyElementsOf(library);
      assertThat(fixture.game.abilities).containsExactly(ability);
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void endingTurnsSkipsConcededSeatsAndWrapsEvenForALoneSurvivor(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    fixture.game.active = format.seatCapacity() - 1;
    fixture.apply(fixture.game.active, END_TURN, Map.of());
    assertThat(fixture.game.active).isZero();
    for (int seat = 1; seat < format.seatCapacity(); seat++) {
      fixture.game.players.get(seat).conceded = true;
    }
    fixture.apply(0, END_TURN, Map.of());
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.game.phase).isEqualTo(UNTAP);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void admissionAndRevisionValidationPrecedeTheTurnTransition(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.reject(0, END_TURN, Map.of(), "Game is not playing");
    fixture.startGame();
    final long revision = fixture.game.revision;
    final List<String> log = List.copyOf(fixture.game.log);
    assertThatThrownBy(
            () ->
                fixture.engine.apply(
                    fixture.game, 0, new Game.Command("stale", revision - 1, END_TURN, Map.of())))
        .hasMessage("Stale revision");
    fixture.game.players.getFirst().conceded = true;
    fixture.reject(0, END_TURN, Map.of(), "You have conceded");
    fixture.game.status = "finished";
    fixture.reject(0, END_TURN, Map.of(), "Game is not playing");
    assertThat(fixture.game.revision).isEqualTo(revision);
    assertThat(fixture.game.log).containsExactlyElementsOf(log);
  }
}
