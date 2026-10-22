package dev.mtgcmd.game;

import static dev.mtgcmd.game.LifecycleFixture.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LifecycleAdmissionTest {
  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void readinessDefaultsAndExplicitUnreadyPreserveWarningAcceptanceRules(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    final Game.Player player = fixture.game.players.getFirst();
    player.warnings = List.of();
    fixture.apply(0, READY, Map.of());
    assertThat(player.ready).isTrue();
    assertThat(player.acceptedWarnings).isFalse();
    player.warnings = List.of("Review deck");
    fixture.reject(0, READY, Map.of(), "Accept deck warnings before becoming ready");
    fixture.apply(0, READY, Map.of(ACCEPT_WARNINGS, true));
    assertThat(player.ready).isTrue();
    assertThat(player.acceptedWarnings).isTrue();
    player.deck.clear();
    fixture.apply(0, READY, Map.of(READY_FLAG, false));
    assertThat(player.ready).isFalse();
    assertThat(player.acceptedWarnings).isFalse();
    fixture.reject(0, READY, Map.of(ACCEPT_WARNINGS, true), "Import a deck first");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void readinessRejectsMalformedFlagsAndStartedGamesBeforeParsingFlags(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.reject(0, READY, Map.of(READY_FLAG, "true"), "Invalid ready");
    fixture.reject(0, READY, Map.of(ACCEPT_WARNINGS, 1), "Invalid acceptWarnings");
    fixture.game.status = "playing";
    fixture.reject(0, READY, Map.of(READY_FLAG, "true"), STARTED_ERROR);
    assertThat(fixture.game.players.getFirst().ready).isFalse();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void startingRequiresHostLobbyFullTableAndReadinessInThatOrder(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    final String readyError =
        format.usesCommanders()
            ? "Four ready players are required"
            : "Two ready players are required";
    fixture.reject(1, START, Map.of(), "Only the host can start");
    fixture.reject(0, START, Map.of(), readyError);
    fixture.readyAllPlayers();
    final Game.Player last = fixture.game.players.removeLast();
    fixture.reject(0, START, Map.of(), readyError);
    fixture.game.players.add(last);
    fixture.apply(0, START, Map.of());
    fixture.reject(0, START, Map.of(), STARTED_ERROR);
    fixture.reject(1, START, Map.of(), "Only the host can start");
    assertThat(fixture.game.status).isEqualTo("opening");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void startupCreatesOwnedCardsOpeningHandsAndOnlyDesignatedCommanders(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    for (final Game.Player player : fixture.game.players) {
      final List<Game.Card> hand = GameLookup.zone(fixture.game, player.seat, GameZones.HAND);
      final List<Game.Card> command = GameLookup.zone(fixture.game, player.seat, GameZones.COMMAND);
      assertThat(hand).hasSize(7);
      assertThat(command).hasSize(format.usesCommanders() ? 1 : 0);
      assertThat(GameLookup.zone(fixture.game, player.seat, GameZones.LIBRARY))
          .hasSize(player.deck.size() - 7 - command.size());
      assertThat(fixture.game.cards.stream().filter(card -> card.owner == player.seat))
          .hasSize(player.deck.size())
          .allSatisfy(
              card -> {
                assertThat(card.controller).isEqualTo(player.seat);
                assertThat(card.zoneOwner).isEqualTo(player.seat);
              });
      if (format.usesCommanders()) {
        assertThat(command.getFirst().definition.id()).isEqualTo(player.commanders.getFirst());
        assertThat(command.getFirst().commanderKey).isNotBlank();
      }
      assertThat(hand).allSatisfy(card -> assertThat(card.commanderKey).isNull());
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void firstPlayerSelectionRetainsFormatSpecificDefaultsAndValidation(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.readyAllPlayers();
    if (format == GameFormat.STANDARD) {
      for (final Object value : List.of(-1, 2, 0.5, "1")) {
        fixture.reject(0, START, Map.of(FIRST_PLAYER, value), "Invalid firstPlayer");
      }
      fixture.apply(0, START, Map.of(FIRST_PLAYER, 1));
      assertThat(fixture.game.active).isEqualTo(1);
      final LifecycleFixture defaults = new LifecycleFixture(format);
      defaults.game.host = 1;
      defaults.startGame();
      assertThat(defaults.game.active).isEqualTo(1);
    } else {
      fixture.apply(0, START, Map.of(FIRST_PLAYER, "ignored"));
      assertThat(fixture.game.active).isZero();
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void onlyTheHostCanFinishIncludingAfterConceding(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    fixture.reject(1, FINISH, Map.of(), "Only the host can finish");
    fixture.apply(0, CONCEDE, Map.of());
    fixture.apply(0, FINISH, Map.of());
    assertThat(fixture.game.status).isEqualTo("finished");
    fixture.reject(0, PHASE, Map.of(), "Game is not playing");
  }
}
