package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TurnReturnTest {
  private static final String RETURN = "GIVE_BACK_TURN";
  private static final String END = "END_TURN";
  private static final String MAIN = "Main 2";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void returnsToTheActualPreviousTurnWithoutUndoingGameplay(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    fixture.game.phase = MAIN;
    apply(fixture, END);
    fixture.game.cards.getFirst().tapped = true;
    fixture.game.cards.getFirst().combat = "attacker";
    fixture.game.players.getFirst().counters.put("life", 13);
    fixture.game.grants.put(1, new HashSet<>(Set.of(0)));
    fixture.game.searches.put(1, 3);
    final Game.Ability ability = new Game.Ability();
    ability.text = "Unresolved ability";
    fixture.game.abilities.add(ability);
    final ObjectNode unchanged = gameplay(fixture.game);
    final long revision = fixture.game.revision;
    apply(fixture, RETURN);
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.game.turn).isEqualTo(1);
    assertThat(fixture.game.phase).isEqualTo(MAIN);
    assertThat(fixture.game.revision).isEqualTo(revision + 1);
    assertThat(gameplay(fixture.game)).isEqualTo(unchanged);
    assertThat(fixture.game.log.getLast())
        .contains("gave the turn back to Player 0", "turn 1", MAIN);
    assertThat(fixture.view(0).path("turnVisit").asLong(-1)).isEqualTo(2);
    assertThat(fixture.view(0).path("canGiveBackTurn").asBoolean()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void repeatedReturnsWalkHistoryAndForwardPlayRecordsTheCorrectedTurn(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    fixture.game.phase = "Draw";
    apply(fixture, END);
    fixture.game.phase = MAIN;
    apply(fixture, END);
    apply(fixture, RETURN);
    assertThat(fixture.game.active).isEqualTo(1);
    assertThat(fixture.game.phase).isEqualTo(MAIN);
    assertThat(fixture.game.turn).isEqualTo(2);
    apply(fixture, RETURN);
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.game.phase).isEqualTo("Draw");
    assertThat(fixture.game.turn).isEqualTo(1);
    fixture.reject(0, RETURN, Map.of(), "No previous turn is available");
    fixture.game.phase = "Upkeep";
    apply(fixture, END);
    apply(fixture, RETURN);
    assertThat(fixture.game.phase).isEqualTo("Upkeep");
    assertThat(fixture.game.turn).isEqualTo(1);
    assertThat(fixture.view(0).path("turnVisit").asLong(-1)).isEqualTo(6);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void onlyEligibleActivePlayersSeeAndExecuteTheReturn(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    fixture.reject(0, RETURN, Map.of(), "No previous turn is available");
    apply(fixture, END);
    assertThat(fixture.view(1).path("canGiveBackTurn").asBoolean()).isTrue();
    assertThat(fixture.view(0).path("canGiveBackTurn").asBoolean()).isFalse();
    final ObjectNode before = MAPPER.valueToTree(fixture.game);
    fixture.reject(0, RETURN, Map.of(), "Only the active player can give back the turn");
    assertThat(MAPPER.<ObjectNode>valueToTree(fixture.game)).isEqualTo(before);
    final Game.Command stale =
        new Game.Command("stale-return", fixture.game.revision - 1, RETURN, Map.of());
    assertThatThrownBy(() -> fixture.engine.apply(fixture.game, 1, stale))
        .hasMessage("Stale revision");
    fixture.game.players.get(1).conceded = true;
    fixture.reject(1, RETURN, Map.of(), "You have conceded");
    fixture.game.status = "finished";
    fixture.reject(1, RETURN, Map.of(), "Game is not playing");
    assertThat(fixture.view(1).path("canGiveBackTurn").asBoolean()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void allForwardHandoffSourcesRecordCheckpointsButOrdinaryPhasesDoNot(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    apply(fixture, "PHASE");
    assertThat(fixture.view(0).path("turnVisit").asLong(-1)).isZero();
    assertThat(fixture.view(0).path("canGiveBackTurn").asBoolean()).isFalse();
    fixture.game.phase = "Cleanup";
    apply(fixture, "PHASE");
    apply(fixture, RETURN);
    assertThat(fixture.game.phase).isEqualTo("Cleanup");
    fixture.apply(1, "CONCEDE", Map.of());
    assertThat(fixture.view(0).path("turnVisit").asLong(-1)).isEqualTo(2);
    final LifecycleFixture concession = started(format);
    concession.apply(0, "CONCEDE", Map.of());
    assertThat(concession.view(concession.game.active).path("turnVisit").asLong(-1)).isEqualTo(1);
    concession.reject(concession.game.active, RETURN, Map.of(), "Previous player has conceded");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void concededPredecessorsAreNotSkipped(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    apply(fixture, END);
    fixture.apply(0, "CONCEDE", Map.of());
    assertThat(fixture.view(1).path("canGiveBackTurn").asBoolean()).isFalse();
    final ObjectNode before = MAPPER.valueToTree(fixture.game);
    fixture.reject(1, RETURN, Map.of(), "Previous player has conceded");
    assertThat(MAPPER.<ObjectNode>valueToTree(fixture.game)).isEqualTo(before);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void aLoneSurvivorCanCorrectTheirOwnHandoff(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    fixture.game.players.stream().skip(1).forEach(player -> player.conceded = true);
    fixture.game.phase = MAIN;
    apply(fixture, END);
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.view(0).path("canGiveBackTurn").asBoolean()).isTrue();
    apply(fixture, RETURN);
    assertThat(fixture.game.phase).isEqualTo(MAIN);
    assertThat(fixture.game.turn).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void historyIsBoundedAndNeverLeaksIntoPlayerViews(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    for (int index = 0; index < 105; index++) apply(fixture, END);
    final ObjectNode saved = MAPPER.valueToTree(fixture.game);
    assertThat(saved.path("turnTracking").path("history").size()).isEqualTo(100);
    for (int seat = 0; seat < format.seatCapacity(); seat++) {
      assertThat(fixture.view(seat).has("turnTracking")).isFalse();
      assertThat(fixture.view(seat).has("history")).isFalse();
    }
    for (int index = 0; index < 100; index++) apply(fixture, RETURN);
    assertThat(fixture.game.turn).isEqualTo(6);
    fixture.reject(fixture.game.active, RETURN, Map.of(), "No previous turn is available");
    assertThat(fixture.view(fixture.game.active).path("turnVisit").asLong(-1)).isEqualTo(205);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void legacySnapshotsStartFreshAndCurrentHistorySurvivesSerialization(final GameFormat format)
      throws java.io.IOException {
    final LifecycleFixture fixture = started(format);
    fixture.game.phase = MAIN;
    final ObjectNode legacy = MAPPER.valueToTree(fixture.game);
    legacy.remove("turnTracking");
    final Game restored = MAPPER.treeToValue(legacy, Game.class);
    assertThat(fixture.engine.view(restored, 0).get("canGiveBackTurn")).isEqualTo(false);
    fixture.engine.apply(
        restored, 0, new Game.Command("handoff", restored.revision, END, Map.of()));
    final Game reloaded = MAPPER.readValue(MAPPER.writeValueAsBytes(restored), Game.class);
    fixture.engine.apply(
        reloaded, 1, new Game.Command("return", reloaded.revision, RETURN, Map.of()));
    assertThat(reloaded.active).isZero();
    assertThat(reloaded.phase).isEqualTo(MAIN);
    assertThat(fixture.engine.view(reloaded, 0).get("turnVisit")).isEqualTo(2L);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void clientFieldsCannotChooseTheReturnDestination(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    fixture.game.phase = MAIN;
    apply(fixture, END);
    fixture.apply(1, RETURN, Map.of("target", 1, "turn", 99, "phase", "Damage"));
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.game.turn).isEqualTo(1);
    assertThat(fixture.game.phase).isEqualTo(MAIN);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void invalidSavedCheckpointsCannotBeReturnedTo(final GameFormat format) {
    final List<Game.TurnCheckpoint> invalid =
        Arrays.asList(
            new Game.TurnCheckpoint(-1, 1, MAIN), new Game.TurnCheckpoint(99, 1, MAIN),
            new Game.TurnCheckpoint(0, 0, MAIN), new Game.TurnCheckpoint(0, 3, MAIN),
            new Game.TurnCheckpoint(0, 1, "invalid"), new Game.TurnCheckpoint(0, 1, null));
    for (final Game.TurnCheckpoint checkpoint : invalid) {
      final LifecycleFixture fixture = started(format);
      apply(fixture, END);
      fixture.game.turnTracking.history = new ArrayList<>(List.of(checkpoint));
      final ObjectNode before = MAPPER.valueToTree(fixture.game);
      assertThat(fixture.view(1).path("canGiveBackTurn").asBoolean()).isFalse();
      fixture.reject(1, RETURN, Map.of(), "Previous turn is invalid");
      assertThat(MAPPER.<ObjectNode>valueToTree(fixture.game)).isEqualTo(before);
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void visitExhaustionRejectsEveryHandoffWithoutPartialMutation(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    apply(fixture, END);
    fixture.game.turnTracking.visit = 9_007_199_254_740_991L;
    final ObjectNode before = MAPPER.valueToTree(fixture.game);
    assertThat(fixture.view(1).path("canGiveBackTurn").asBoolean()).isFalse();
    for (final String command : List.of(RETURN, END, "CONCEDE")) {
      fixture.reject(1, command, Map.of(), "Turn sequence exhausted");
      assertThat(MAPPER.<ObjectNode>valueToTree(fixture.game)).isEqualTo(before);
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void nullLegacyMetadataDoesNotMutateDuringReadsAndInitializesOnHandoff(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    fixture.game.turnTracking = null;
    assertThat(fixture.view(0).path("turnVisit").asLong(-1)).isZero();
    assertThat(fixture.view(0).path("canGiveBackTurn").asBoolean()).isFalse();
    assertThat(fixture.game.turnTracking).isNull();
    apply(fixture, END);
    apply(fixture, RETURN);
    fixture.game.turnTracking.history = null;
    assertThat(fixture.view(0).path("canGiveBackTurn").asBoolean()).isFalse();
    apply(fixture, END);
    apply(fixture, RETURN);
    assertThat(fixture.game.turn).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void skippedSeatsDoNotReplaceTheActualPredecessor(final GameFormat format) {
    final LifecycleFixture fixture = started(format);
    fixture.game.players.get(1).conceded = true;
    fixture.game.phase = MAIN;
    apply(fixture, END);
    assertThat(fixture.game.active).isEqualTo(2 % format.seatCapacity());
    apply(fixture, RETURN);
    assertThat(fixture.game.active).isZero();
    assertThat(fixture.game.phase).isEqualTo(MAIN);
    assertThat(fixture.game.turn).isEqualTo(1);
  }

  private static LifecycleFixture started(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    return fixture;
  }

  private static void apply(final LifecycleFixture fixture, final String type) {
    final long revision = fixture.game.revision;
    fixture.engine.apply(
        fixture.game,
        fixture.game.active,
        new Game.Command(UUID.randomUUID().toString(), revision, type, Map.of()));
    assertThat(fixture.game.revision).isEqualTo(revision + 1);
  }

  private static ObjectNode gameplay(final Game game) {
    final ObjectNode node = MAPPER.valueToTree(game);
    node.remove(List.of("active", "turn", "phase", "revision", "log", "turnTracking"));
    return node;
  }
}
