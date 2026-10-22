package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MulliganTest {
  private static final String MODE_FIELD = "mode";
  private static final String FREE_FIELD = "freeMulligans";
  private static final String LONDON_MODE = "LONDON";
  private static final String UNLIMITED_MODE = "UNLIMITED";
  private static final String START_COMMAND = "START";
  private static final String OPENING_STATUS = "opening";
  private static final String PLAYING_STATUS = "playing";
  private static final String CONFIGURE = "CONFIGURE_MULLIGANS";
  private static final String REDRAW = "MULLIGAN";
  private static final String KEEP = "KEEP_HAND";
  private static final String BOTTOM = "bottom";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void startEntersPrivateOpeningStageAndOnlyFinalKeepEnablesPlay(final GameFormat format) {
    final LifecycleFixture table = opening(format);
    assertThat(table.game.status).isEqualTo(OPENING_STATUS);
    assertThat(table.game.mulligans.hands).hasSize(format.seatCapacity());
    for (final Game.Player player : table.game.players) {
      assertThat(table.game.mulligans.hands)
          .containsEntry(player.seat, new Game.OpeningHand(0, false));
    }
    final JsonNode view = table.view(0);
    assertThat(view.at("/mulligans/mode").asText()).isEqualTo(LONDON_MODE);
    assertThat(view.at("/mulligans/freeMulligans").asInt())
        .isEqualTo(format.usesCommanders() ? 1 : 0);
    final Set<String> own = new HashSet<>(handIds(table.game, 0));
    assertThat(own).hasSize(7);
    for (int seat = 1; seat < format.seatCapacity(); seat++) {
      assertThat(table.view(seat).path("cards").findValuesAsText("id"))
          .doesNotContainAnyElementsOf(own);
    }
    for (final String command :
        List.of("DRAW", "MOVE", "PHASE", "END_TURN", "SEARCH", "GRANT", "CONCEDE")) {
      reject(table, 0, command, Map.of(), "Choose opening hands before playing");
    }
    for (int seat = 0; seat < format.seatCapacity(); seat++) {
      table.apply(seat, KEEP, Map.of(BOTTOM, List.of()));
      if (seat < format.seatCapacity() - 1) {
        assertThat(table.game.status).isEqualTo(OPENING_STATUS);
        reject(table, seat, REDRAW, Map.of(), "Opening hand already kept");
        reject(table, seat, KEEP, Map.of(), "Opening hand already kept");
      }
    }
    assertThat(table.game.status).isEqualTo(PLAYING_STATUS);
    assertThat(handIds(table.game, 0)).containsExactlyInAnyOrderElementsOf(own);
    assertThat(table.game.turn).isEqualTo(1);
    assertThat(table.game.phase).isEqualTo("Untap");
    assertThat(table.game.active).isEqualTo(format == GameFormat.STANDARD ? 1 : 0);
    reject(table, 0, REDRAW, Map.of(), "Opening hands are not being chosen");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void londonRedrawsSevenRekeysThenBottomsExactlyInSubmittedOrder(final GameFormat format) {
    final LifecycleFixture table = opening(format);
    final List<String> original = handIds(table.game, 0);
    final JsonNode playersBefore = MAPPER.valueToTree(table.game.players);
    final JsonNode othersBefore =
        MAPPER.valueToTree(table.game.cards.stream().filter(c -> c.owner != 0).toList());
    final List<String> commanders =
        table.game.cards.stream().filter(c -> c.commanderKey != null).map(c -> c.id).toList();
    final int redraws = format.usesCommanders() ? 3 : 2;
    for (int index = 0; index < redraws; index++) {
      table.apply(0, REDRAW, Map.of());
      assertThat(handIds(table.game, 0)).hasSize(7).doesNotContainAnyElementsOf(original);
    }
    assertThat(
            table.game.cards.stream().filter(c -> c.commanderKey != null).map(c -> c.id).toList())
        .containsExactlyElementsOf(commanders);
    final List<String> hand = handIds(table.game, 0);
    final List<String> libraryBefore =
        GameLookup.zone(table.game, 0, GameZones.LIBRARY).stream().map(c -> c.id).toList();
    final List<String> bottom = List.of(hand.get(4), hand.get(1));
    final List<String> names =
        bottom.stream().map(id -> GameLookup.find(table.game, id).definition.name()).toList();
    reject(table, 0, KEEP, Map.of(BOTTOM, List.of()), "Select exactly 2 cards to bottom");
    reject(
        table,
        0,
        KEEP,
        Map.of(BOTTOM, List.of(hand.getFirst(), hand.getFirst())),
        "Duplicate bottom card");
    reject(
        table,
        0,
        KEEP,
        Map.of(BOTTOM, List.of(original.getFirst(), hand.getFirst())),
        "Choose cards from your current hand");
    table.apply(0, KEEP, Map.of(BOTTOM, bottom));
    assertThat(table.view(0).at("/mulligans/players/0/bottomCount").asInt()).isZero();
    assertThat(table.game.log.getLast()).contains("kept", "opening hand");
    assertThat(handIds(table.game, 0)).hasSize(5).doesNotContainAnyElementsOf(bottom);
    final List<Game.Card> library = GameLookup.zone(table.game, 0, GameZones.LIBRARY);
    assertThat(library.subList(0, libraryBefore.size()))
        .extracting(c -> c.id)
        .containsExactlyElementsOf(libraryBefore);
    assertThat(library.subList(library.size() - 2, library.size()))
        .extracting(c -> c.definition.name())
        .containsExactlyElementsOf(names);
    assertThat(table.game.cards).extracting(c -> c.id).doesNotContainAnyElementsOf(bottom);
    assertThat(table.game.log.getLast()).doesNotContain(names.toArray(String[]::new));
    assertThat(MAPPER.<JsonNode>valueToTree(table.game.players)).isEqualTo(playersBefore);
    assertThat(
            MAPPER.<JsonNode>valueToTree(
                table.game.cards.stream().filter(c -> c.owner != 0).toList()))
        .isEqualTo(othersBefore);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void settingsAreHostOwnedValidatedAndResetReadinessOnlyOnChange(final GameFormat format) {
    final LifecycleFixture table = new LifecycleFixture(format);
    table.readyAllPlayers();
    reject(table, 0, CONFIGURE, Map.of(MODE_FIELD, LONDON_MODE), "Invalid freeMulligans");
    reject(
        table,
        1,
        CONFIGURE,
        Map.of(MODE_FIELD, UNLIMITED_MODE, FREE_FIELD, 0),
        "Only the host can configure mulligans");
    for (final Object count : List.of(-1, 101, 0.5, "1")) {
      reject(
          table,
          0,
          CONFIGURE,
          Map.of(MODE_FIELD, LONDON_MODE, FREE_FIELD, count),
          "Invalid freeMulligans");
    }
    reject(
        table, 0, CONFIGURE, Map.of(MODE_FIELD, "OTHER", FREE_FIELD, 0), "Unknown mulligan mode");
    table.apply(
        0, CONFIGURE, Map.of(MODE_FIELD, LONDON_MODE, FREE_FIELD, format.usesCommanders() ? 1 : 0));
    assertThat(table.game.players).allMatch(p -> p.ready);
    assertThat(table.game.log.getLast()).contains("mulligan settings", "unchanged");
    table.apply(0, CONFIGURE, Map.of(MODE_FIELD, UNLIMITED_MODE, FREE_FIELD, 0));
    assertThat(table.game.log.getLast()).contains("changed", "mulligan settings");
    assertThat(table.game.players).allMatch(p -> !p.ready && !p.acceptedWarnings);
    table.readyAllPlayers();
    table.apply(0, START_COMMAND, Map.of());
    reject(
        table,
        0,
        CONFIGURE,
        Map.of(MODE_FIELD, LONDON_MODE, FREE_FIELD, 0),
        "Mulligan settings are locked");
    for (int index = 0; index < 10; index++) {
      table.apply(0, REDRAW, Map.of());
    }
    assertThat(handIds(table.game, 0)).hasSize(7);
    table.apply(0, KEEP, Map.of(BOTTOM, List.of()));
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void openingProgressRoundTripsAndLegacyPlayingGamesStayPlaying(final GameFormat format)
      throws Exception {
    final LifecycleFixture table = opening(format);
    table.apply(0, REDRAW, Map.of());
    final Game restored = MAPPER.readValue(MAPPER.writeValueAsBytes(table.game), Game.class);
    assertThat(MAPPER.<JsonNode>valueToTree(table.engine.view(restored, 0)))
        .isEqualTo(table.view(0));
    final JsonNode data = MAPPER.readTree("{\"status\":\"playing\"}");
    final Game legacy = MAPPER.treeToValue(data, Game.class);
    legacy.format = format;
    assertThat(legacy.status).isEqualTo(PLAYING_STATUS);
    final LifecycleFixture playing = new LifecycleFixture(format);
    playing.startGame();
    final com.fasterxml.jackson.databind.node.ObjectNode saved = MAPPER.valueToTree(playing.game);
    saved.remove("mulligans");
    final Game oldGame = MAPPER.treeToValue(saved, Game.class);
    oldGame.mulligans.hands = null;
    final JsonNode beforeRead = MAPPER.valueToTree(oldGame);
    assertThat(playing.engine.view(oldGame, 0)).containsEntry("status", PLAYING_STATUS);
    assertThat(MAPPER.<JsonNode>valueToTree(oldGame)).isEqualTo(beforeRead);
    playing.engine.apply(
        oldGame,
        0,
        new Game.Command(UUID.randomUUID().toString(), oldGame.revision, "DRAW", Map.of()));
    assertThat(handIds(oldGame, 0)).hasSize(8);
    reject(table, 0, KEEP, Collections.singletonMap(BOTTOM, null), "Invalid bottom cards");
    reject(table, 0, KEEP, Map.of(BOTTOM, List.of(42)), "Invalid bottom cards");
    if (format.usesCommanders()) {
      table.apply(0, REDRAW, Map.of());
    }
    reject(
        table,
        0,
        KEEP,
        Map.of(BOTTOM, List.of(handIds(table.game, 1).getFirst())),
        "Choose cards from your current hand");
  }

  private static LifecycleFixture opening(final GameFormat format) {
    final LifecycleFixture table = new LifecycleFixture(format);
    table.readyAllPlayers();
    table.apply(
        0, START_COMMAND, format == GameFormat.STANDARD ? Map.of("firstPlayer", 1) : Map.of());
    return table;
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void settingsBoundsLegacyNullsAndMalformedBottomSelectionsAreSafe(final GameFormat format) {
    final LifecycleFixture table = new LifecycleFixture(format);
    table.game.mulligans = null;
    final JsonNode before = MAPPER.valueToTree(table.game);
    assertThat(table.view(0).at("/mulligans/mode").asText()).isEqualTo(LONDON_MODE);
    assertThat(MAPPER.<JsonNode>valueToTree(table.game)).isEqualTo(before);
    table.apply(0, CONFIGURE, Map.of(MODE_FIELD, LONDON_MODE, FREE_FIELD, 100));
    assertThat(table.view(1).at("/mulligans/freeMulligans").asInt()).isEqualTo(100);
    table.readyAllPlayers();
    table.apply(0, START_COMMAND, Map.of());
    table.game.mulligans.hands.put(0, new Game.OpeningHand(100, false));
    assertThat(table.view(0).at("/mulligans/players/0/bottomCount").asInt()).isZero();
    table.apply(0, REDRAW, Map.of());
    assertThat(table.view(0).at("/mulligans/players/0/bottomCount").asInt()).isEqualTo(1);
    for (final Object invalid :
        List.of(
            "not a list",
            Collections.singletonList(null),
            List.of(" "),
            List.of("x".repeat(81)),
            Collections.nCopies(8, "id"))) {
      reject(table, 0, KEEP, Map.of(BOTTOM, invalid), "Invalid bottom cards");
    }
    final JsonNode started = MAPPER.valueToTree(table.game);
    assertThatThrownBy(() -> table.engine.join(table.game, "Late player"))
        .hasMessage("Game has already started");
    assertThatThrownBy(() -> table.engine.load(table.game, 0, List.of(), List.of()))
        .hasMessage("Decks cannot change after start");
    assertThat(MAPPER.<JsonNode>valueToTree(table.game)).isEqualTo(started);
    final String legacyId = "x".repeat(80);
    GameLookup.find(table.game, handIds(table.game, 0).getFirst()).id = legacyId;
    table.apply(0, KEEP, Map.of(BOTTOM, List.of(legacyId)));
    assertThat(handIds(table.game, 0)).hasSize(6);
    final LifecycleFixture unlimited = new LifecycleFixture(format);
    unlimited.apply(0, CONFIGURE, Map.of(MODE_FIELD, UNLIMITED_MODE, FREE_FIELD, 0));
    unlimited.readyAllPlayers();
    unlimited.apply(0, START_COMMAND, Map.of());
    unlimited.game.mulligans.hands.put(0, new Game.OpeningHand(Integer.MAX_VALUE, false));
    reject(unlimited, 0, REDRAW, Map.of(), "Too many mulligans");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void shortAndEmptyLibrariesCannotStrandOpeningAndPaidRedrawsStopAtZero(final GameFormat format) {
    final LifecycleFixture table = opening(format);
    for (int index = 0; index < 7 + (format.usesCommanders() ? 1 : 0); index++) {
      table.apply(0, REDRAW, Map.of("target", 1));
    }
    assertThat(handIds(table.game, 1)).hasSize(7);
    reject(table, 0, REDRAW, Map.of(), "No further London mulligans");
    table.apply(0, KEEP, Map.of(BOTTOM, handIds(table.game, 0)));
    assertThat(handIds(table.game, 0)).isEmpty();
    final LifecycleFixture empty = opening(format);
    empty.game.cards.removeIf(c -> c.zoneOwner == 0 && !GameZones.COMMAND.equals(c.zone));
    reject(empty, 0, REDRAW, Map.of(), "No cards to redraw");
    empty.apply(0, KEEP, Map.of());
    assertThat(empty.view(0).at("/mulligans/players/0/kept").asBoolean()).isTrue();
    final LifecycleFixture shortDeck = new LifecycleFixture(format);
    shortDeck.game.players.getFirst().deck = shortDeck.game.players.getFirst().deck.subList(0, 3);
    shortDeck.readyAllPlayers();
    shortDeck.apply(0, START_COMMAND, Map.of());
    final int count = format.usesCommanders() ? 2 : 3;
    assertThat(handIds(shortDeck.game, 0)).hasSize(count);
    for (int index = 0; index < count + (format.usesCommanders() ? 1 : 0); index++) {
      shortDeck.apply(0, REDRAW, Map.of());
    }
    shortDeck.apply(0, KEEP, Map.of(BOTTOM, handIds(shortDeck.game, 0)));
    assertThat(handIds(shortDeck.game, 0)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void openingIgnoresLegacyGrantsRevealsAndSearchesAndHostCanCancel(final GameFormat format) {
    final LifecycleFixture table = opening(format);
    table.game.grants.put(0, Set.of(1));
    table.game.searches.put(1, 0);
    table.game.cards.stream().filter(c -> c.zoneOwner == 0).forEach(c -> c.revealedTo.add(1));
    final Set<String> privateIds = new HashSet<>();
    table.game.cards.stream()
        .filter(c -> c.zoneOwner == 0 && Set.of(GameZones.HAND, GameZones.LIBRARY).contains(c.zone))
        .forEach(c -> privateIds.add(c.id));
    assertThat(table.view(1).path("cards").findValuesAsText("id"))
        .doesNotContainAnyElementsOf(privateIds);
    assertThat(table.view(0).path("cards").findValuesAsText("id"))
        .containsAll(handIds(table.game, 0));
    reject(table, 1, "FINISH", Map.of(), "Only the host can finish");
    table.apply(0, "FINISH", Map.of());
    assertThat(table.game.status).isEqualTo("finished");
    reject(table, 1, KEEP, Map.of(), "Opening hands are not being chosen");
  }

  private static List<String> handIds(final Game game, final int seat) {
    return GameLookup.zone(game, seat, GameZones.HAND).stream().map(c -> c.id).toList();
  }

  private static void reject(
      final LifecycleFixture table,
      final int seat,
      final String type,
      final Map<String, Object> data,
      final String message) {
    final JsonNode before = MAPPER.valueToTree(table.game);
    table.reject(seat, type, data, message);
    assertThat(MAPPER.<JsonNode>valueToTree(table.game)).isEqualTo(before);
  }
}
