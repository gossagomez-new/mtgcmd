package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Lifecycle commands use the public engine; fixtures load decks before readiness. */
final class LifecycleFixture {
  static final String READY = "READY";
  static final String START = "START";
  static final String PHASE = "PHASE";
  static final String CONCEDE = "CONCEDE";
  static final String FINISH = "FINISH";
  static final String READY_FLAG = "ready";
  static final String ACCEPT_WARNINGS = "acceptWarnings";
  static final String FIRST_PLAYER = "firstPlayer";
  static final String STARTED_ERROR = "Game has already started";
  static final String UNTAP = "Untap";
  static final String COMBAT = "Attackers";
  final GameEngine engine = new GameEngine();
  final Game game = new Game();

  LifecycleFixture(final GameFormat format) {
    game.format = format;
    for (int seat = 0; seat < format.seatCapacity(); seat++) {
      engine.join(game, "Player " + seat);
      final List<Game.Definition> deck = new ArrayList<>();
      for (int index = 0; index < (format == GameFormat.STANDARD ? 60 : 20); index++) {
        deck.add(definition(seat + ":" + index));
      }
      engine.load(
          game, seat, deck, format.usesCommanders() ? List.of(deck.getFirst().id()) : List.of());
    }
  }

  static Game.Definition definition(final String identifier) {
    return new Game.Definition(
        identifier, identifier, "", "", "Legendary Creature", List.of(), true, List.of());
  }

  void apply(final int seat, final String type, final Map<String, Object> data) {
    final long revision = game.revision;
    final int entries = game.log.size();
    engine.apply(game, seat, command(type, data));
    assertThat(game.revision).isEqualTo(revision + 1);
    assertThat(game.log).hasSize(entries + 1);
  }

  void reject(
      final int seat, final String type, final Map<String, Object> data, final String message) {
    final long revision = game.revision;
    final List<String> log = List.copyOf(game.log);
    assertThatThrownBy(() -> engine.apply(game, seat, command(type, data)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(message);
    assertThat(game.revision).isEqualTo(revision);
    assertThat(game.log).containsExactlyElementsOf(log);
  }

  private Game.Command command(final String type, final Map<String, Object> data) {
    return new Game.Command(UUID.randomUUID().toString(), game.revision, type, data);
  }

  void readyAllPlayers() {
    for (final Game.Player player : game.players) {
      apply(player.seat, READY, Map.of(ACCEPT_WARNINGS, true));
    }
  }

  void startGame() {
    readyAllPlayers();
    apply(game.host, START, Map.of());
  }

  JsonNode view(final int seat) {
    return new ObjectMapper().valueToTree(engine.view(game, seat));
  }
}
