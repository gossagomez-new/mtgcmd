package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import dev.mtgcmd.game.Game.Definition;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class GameEngineCommandsTest {
  private static final String START_COMMAND = "START";

  private static final String UNKNOWN_COMMAND = "UNKNOWN";

  private final GameEngine engine = new GameEngine();
  private final GameCommandData commandData = new GameCommandData(engine);

  static Stream<Arguments> commands() {
    final List<String> types =
        List.of(
            "READY",
            START_COMMAND,
            "DRAW",
            "SHUFFLE",
            "SEARCH",
            "CLOSE_SEARCH",
            "GRANT",
            "MOVE",
            "TAP",
            "UNTAP_ALL",
            "FACE_DOWN",
            "TRANSFORM",
            "REVEAL",
            "CONTROL",
            "ATTACH",
            "CARD_COUNTER",
            "COUNTER",
            "COMMANDER_CAST",
            "TOKEN",
            "EDIT_TOKEN",
            "COPY",
            "POSITION",
            "COMBAT",
            "ABILITY",
            "RESOLVE_ABILITY",
            "PHASE",
            "DICE",
            "CONCEDE",
            "FINISH");
    return Arrays.stream(GameFormat.values())
        .flatMap(
            format ->
                types.stream()
                    .filter(
                        type -> format == GameFormat.COMMANDER || !"COMMANDER_CAST".equals(type))
                    .map(type -> Arguments.of(format, type)));
  }

  Game ready(final GameFormat format) {
    final Game game = new Game();
    game.format = format;
    final Definition definition =
        new Game.Definition(
            "island",
            "Island",
            "",
            "",
            "Basic Land — Island",
            List.of("U"),
            true,
            List.of(),
            Map.of("standard", "legal"),
            System.currentTimeMillis());
    for (int seat = 0; seat < format.seatCapacity(); seat++) {
      engine.join(game, "Player " + seat);
      engine.load(
          game,
          seat,
          Collections.nCopies(format == GameFormat.STANDARD ? 60 : 20, definition),
          format.usesCommanders() ? List.of("island") : List.of());
      apply(game, seat, "READY", Map.of("acceptWarnings", true));
    }
    return game;
  }

  Game playing(final GameFormat format) {
    final Game game = ready(format);
    apply(game, 0, START_COMMAND, Map.of());
    return game;
  }

  void apply(final Game game, final int seat, final String type, final Map<String, Object> data) {
    engine.apply(
        game, seat, new Game.Command(UUID.randomUUID().toString(), game.revision, type, data));
  }

  @ParameterizedTest
  @MethodSource("commands")
  void everyCommandAdvancesExactlyOneRevisionAndRecordsItsExistingDescription(
      final GameFormat format, final String type) {
    final Game game =
        "READY".equals(type) || START_COMMAND.equals(type) ? ready(format) : playing(format);
    final Map<String, Object> data = commandData.forCommand(game, type);
    final long initialRevision = game.revision;
    final int entries = game.log.size();
    apply(game, 0, type, data);
    assertThat(game.revision).isEqualTo(initialRevision + 1);
    assertThat(game.log).hasSize(entries + 1);
    final String description =
        switch (type) {
          case "DRAW" -> "drew 1 card(s)";
          case "SHUFFLE" -> "shuffled a library";
          case "SEARCH" -> "opened a private library search";
          case "PHASE" -> "advanced to Upkeep";
          default -> type.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    if ("DICE".equals(type)) {
      assertThat(game.log.getLast()).matches("Player 0: rolled [1-6]");
    } else {
      assertThat(game.log.getLast()).isEqualTo("Player 0: " + description);
    }
  }

  @Test
  void validationPrecedenceAndRejectedCommandBookkeepingRemainCompatible() {
    final Game game = ready(GameFormat.COMMANDER);
    final long initialRevision = game.revision;
    List<String> log = List.copyOf(game.log);
    assertThatThrownBy(() -> engine.apply(game, -1, null)).hasMessage("Invalid command");
    assertThatThrownBy(
            () ->
                engine.apply(
                    game,
                    -1,
                    new Game.Command("id", initialRevision - 1, UNKNOWN_COMMAND, Map.of())))
        .hasMessage("Stale revision");
    assertThatThrownBy(() -> apply(game, -1, UNKNOWN_COMMAND, Map.of())).hasMessage("Unknown seat");
    assertThatThrownBy(() -> apply(game, 0, UNKNOWN_COMMAND, Map.of()))
        .hasMessage("Game is not playing");
    assertThat(game.revision).isEqualTo(initialRevision);
    assertThat(game.log).containsExactlyElementsOf(log);
    apply(game, 0, START_COMMAND, Map.of());
    apply(game, 1, "CONCEDE", Map.of());
    final long revision = game.revision;
    log = List.copyOf(game.log);
    assertThatThrownBy(() -> apply(game, 1, UNKNOWN_COMMAND, Map.of()))
        .hasMessage("You have conceded");
    assertThatThrownBy(() -> apply(game, 1, "FINISH", Map.of()))
        .hasMessage("Only the host can finish");
    assertThatThrownBy(() -> apply(game, 0, UNKNOWN_COMMAND, Map.of()))
        .hasMessage("Unknown command");
    assertThat(game.revision).isEqualTo(revision);
    assertThat(game.log).containsExactlyElementsOf(log);
  }

  @Test
  void concededHostCanFinishAndLogHistoryRemainsBounded() {
    final Game game = playing(GameFormat.STANDARD);
    game.log.clear();
    for (int i = 0; i < 105; i++) {
      apply(game, 0, "COUNTER", Map.of("name", "life", "delta", 0));
    }
    assertThat(game.log).hasSize(100);
    apply(game, 0, "CONCEDE", Map.of());
    apply(game, 0, "FINISH", Map.of());
    assertThat(game.status).isEqualTo("finished");
    assertThat(game.log).hasSize(100);
  }
}
