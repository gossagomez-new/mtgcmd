package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CounterActionsTest {
  private static final String COUNTER = "COUNTER";
  private static final String CARD_COUNTER = "CARD_COUNTER";
  private static final String COMMANDER_CAST = "COMMANDER_CAST";
  private static final String NAME = "name";
  private static final String VALUE = "value";
  private static final String DELTA = "delta";
  private static final String KEY = "key";
  private static final String SIDES = "sides";
  private static final String MARK = "mark";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void playerAndCardCountersPreserveExactValuesDefaultsAndCapacity(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    final Game.Card card = table.game.cards.getFirst();
    card.zone = GameZones.BATTLEFIELD;
    card.controller = 0;
    for (final String command : List.of(COUNTER, CARD_COUNTER)) {
      final Map<String, Integer> counters =
          COUNTER.equals(command) ? table.game.players.getFirst().counters : card.counters;
      counters.clear();
      apply(table, command, card, Map.of(NAME, " mark ", VALUE, 7, DELTA, "ignored"));
      assertThat(counters).containsExactlyEntriesOf(Map.of(MARK, 7));
      apply(table, command, card, Map.of(NAME, MARK, DELTA, -2));
      assertThat(counters.get(MARK)).isEqualTo(5);
      apply(table, command, card, Map.of(NAME, MARK));
      assertThat(counters.get(MARK)).isEqualTo(5);
      apply(table, command, card, Map.of(NAME, "new"));
      assertThat(counters.get("new")).isZero();
      counters.clear();
      for (int index = 0; index < 99; index++) {
        counters.put("counter" + index, 0);
      }
      apply(table, command, card, Map.of(NAME, MARK, VALUE, 99_999));
      assertThat(counters).hasSize(100).containsEntry(MARK, 99_999);
      reject(table, command, card, Map.of(NAME, MARK, DELTA, 1), "Counter out of range");
      apply(table, command, card, Map.of(NAME, MARK, DELTA, -9999));
      assertThat(counters.get(MARK)).isEqualTo(90_000);
      apply(table, command, card, Map.of(NAME, MARK, VALUE, -99_999));
      reject(table, command, card, Map.of(NAME, MARK, DELTA, -1), "Counter out of range");
      apply(table, command, card, Map.of(NAME, MARK, DELTA, 9999));
      assertThat(counters.get(MARK)).isEqualTo(-90_000);
      reject(table, command, card, Map.of(NAME, "overflow", VALUE, "bad"), "Too many counters");
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void invalidCounterInputsAreAtomic(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    final Game.Card card = table.game.cards.getFirst();
    card.zone = GameZones.BATTLEFIELD;
    card.controller = 0;
    for (final String command : List.of(COUNTER, CARD_COUNTER)) {
      for (final Object name : List.of("", " ", "x".repeat(101), 42)) {
        reject(table, command, card, Map.of(NAME, name), "Invalid name");
      }
      reject(table, command, card, Map.of(), "Invalid name");
      for (final Object value :
          List.of(-100_000, 100_000, 1.5, "1", Double.NaN, Double.POSITIVE_INFINITY)) {
        reject(table, command, card, Map.of(NAME, MARK, VALUE, value), "Invalid value");
      }
      for (final Object delta : List.of(-10_000, 10_000, 1.5, "1")) {
        reject(table, command, card, Map.of(NAME, MARK, DELTA, delta), "Invalid delta");
      }
      apply(table, command, card, Map.of(NAME, "x".repeat(100), VALUE, 0));
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void cardCounterHonorsControllerAndPrivateAccess(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    final Game.Card card = table.game.cards.getFirst();
    card.owner = 1;
    card.zoneOwner = 1;
    card.controller = 1;
    card.zone = GameZones.BATTLEFIELD;
    reject(
        table,
        CARD_COUNTER,
        card,
        Map.of(NAME, MARK, VALUE, 1),
        "No permission to change this card");
    card.controller = 0;
    apply(table, CARD_COUNTER, card, Map.of(NAME, MARK, VALUE, 3));
    assertThat(card.counters).containsEntry(MARK, 3);
    card.zone = GameZones.HAND;
    reject(
        table,
        CARD_COUNTER,
        card,
        Map.of(NAME, MARK, VALUE, 4),
        "No permission to change this card");
    table.game.grants.put(1, Set.of(0));
    apply(table, CARD_COUNTER, card, Map.of(NAME, MARK, VALUE, 4));
    assertThat(card.counters).containsEntry(MARK, 4);
    card.zone = GameZones.LIBRARY;
    reject(table, CARD_COUNTER, card, Map.of(NAME, MARK), "Open a library search first");
    table.game.searches.put(0, 1);
    apply(table, CARD_COUNTER, card, Map.of(NAME, MARK, VALUE, 5));
    assertThat(card.counters).containsEntry(MARK, 5);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void commanderCastsRequireFormatAndOwnershipAndUpdateOnlyActor(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    if (!format.usesCommanders()) {
      reject(table, COMMANDER_CAST, null, Map.of(), "Standard does not use commanders");
      return;
    }
    final Game.Card own =
        table.game.cards.stream()
            .filter(c -> c.owner == 0 && c.commanderKey != null)
            .findFirst()
            .orElseThrow();
    final Game.Card opponent =
        table.game.cards.stream()
            .filter(c -> c.owner == 1 && c.commanderKey != null)
            .findFirst()
            .orElseThrow();
    reject(table, COMMANDER_CAST, null, Map.of(KEY, opponent.commanderKey), "Unknown commander");
    reject(table, COMMANDER_CAST, null, Map.of(KEY, "missing"), "Unknown commander");
    final String counter = "casts:" + own.commanderKey;
    table.apply(0, COMMANDER_CAST, Map.of(KEY, own.commanderKey));
    assertThat(table.game.players.getFirst().counters).containsEntry(counter, 1);
    table.apply(0, COMMANDER_CAST, Map.of(KEY, own.commanderKey, DELTA, 100));
    assertThat(table.game.players.getFirst().counters).containsEntry(counter, 101);
    table.apply(0, COMMANDER_CAST, Map.of(KEY, own.commanderKey, DELTA, -100));
    assertThat(table.game.players.getFirst().counters).containsEntry(counter, 1);
    assertThat(table.game.players.get(1).counters).doesNotContainKey(counter);
    for (final Object delta : List.of(-101, 101, 0.5, "1")) {
      reject(
          table,
          COMMANDER_CAST,
          null,
          Map.of(KEY, own.commanderKey, DELTA, delta),
          "Invalid delta");
    }
    reject(table, COMMANDER_CAST, null, Map.of(KEY, "missing", DELTA, 101), "Unknown commander");
    reject(table, COMMANDER_CAST, null, Map.of(), "Invalid key");
    reject(table, COMMANDER_CAST, null, Map.of(KEY, "x".repeat(81)), "Invalid key");
    final Map<String, Integer> counters = table.game.players.getFirst().counters;
    counters.put(counter, 99_999);
    reject(table, COMMANDER_CAST, null, Map.of(KEY, own.commanderKey), "Counter out of range");
    counters.clear();
    for (int index = 0; index < 100; index++) {
      counters.put("counter" + index, 0);
    }
    reject(table, COMMANDER_CAST, null, Map.of(KEY, own.commanderKey), "Too many counters");
    counters.remove("counter0");
    counters.put(counter, 0);
    table.apply(0, COMMANDER_CAST, Map.of(KEY, own.commanderKey, DELTA, -1));
    assertThat(counters).hasSize(100).containsEntry(counter, -1);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void facadeRejectsInvalidActorsAndDiceInputsWithoutChangingState(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    for (final Object sides : List.of(1, 1001, 1.5, "6")) {
      reject(table, "DICE", null, Map.of(SIDES, sides), "Invalid sides");
    }
    for (final String command : List.of(COUNTER, CARD_COUNTER, COMMANDER_CAST, "DICE")) {
      final JsonNode before = snapshot(table.game);
      table.reject(-1, command, Map.of(), "Unknown seat");
      assertThat(snapshot(table.game)).isEqualTo(before);
      table.game.players.getFirst().conceded = true;
      reject(table, command, table.game.cards.getFirst(), Map.of(), "You have conceded");
      table.game.players.getFirst().conceded = false;
      table.game.status = "lobby";
      reject(table, command, table.game.cards.getFirst(), Map.of(), "Game is not playing");
      table.game.status = "playing";
    }
    final Map<String, Integer> owner = new HashMap<>(table.game.players.getFirst().counters);
    table.apply(1, COUNTER, Map.of(NAME, MARK, VALUE, 4));
    assertThat(table.game.players.get(1).counters).containsEntry(MARK, 4);
    assertThat(table.game.players.getFirst().counters).containsExactlyInAnyOrderEntriesOf(owner);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void diceUsesRequestedBoundsAndReturnsBothEndpoints(final GameFormat format) {
    final Game game = playing(format).game;
    final SecureRandom random = mock(SecureRandom.class);
    final CounterActions actions = new CounterActions(new GameAccess(), random);
    final JsonNode before = snapshot(game);
    for (final int sides : List.of(2, 6, 1000)) {
      when(random.nextInt(sides)).thenReturn(0, sides - 1);
      assertThat(actions.dice(game, 0, Map.of(SIDES, sides))).isEqualTo("rolled 1");
      assertThat(actions.dice(game, 0, Map.of(SIDES, sides))).isEqualTo("rolled " + sides);
      verify(random, times(2)).nextInt(sides);
    }
    when(random.nextInt(6)).thenReturn(2);
    assertThat(actions.dice(game, 0, Map.of())).isEqualTo("rolled 3");
    clearInvocations(random);
    for (final Object sides : List.of(1, 1001, 2.5, "6")) {
      assertThatThrownBy(() -> actions.dice(game, 0, Map.of(SIDES, sides)))
          .hasMessage("Invalid sides");
    }
    verifyNoInteractions(random);
    assertThat(snapshot(game)).isEqualTo(before);
  }

  private static JsonNode snapshot(final Game game) {
    return MAPPER.valueToTree(game);
  }

  private static LifecycleFixture playing(final GameFormat format) {
    final LifecycleFixture table = new LifecycleFixture(format);
    table.startGame();
    return table;
  }

  private static Map<String, Object> payload(
      final String command, final Game.Card card, final Map<String, Object> data) {
    final Map<String, Object> result = new HashMap<>(data);
    if (CARD_COUNTER.equals(command)) {
      result.put("cardId", card.id);
    }
    return result;
  }

  private static void apply(
      final LifecycleFixture table,
      final String command,
      final Game.Card card,
      final Map<String, Object> data) {
    table.apply(0, command, payload(command, card, data));
  }

  private static void reject(
      final LifecycleFixture table,
      final String command,
      final Game.Card card,
      final Map<String, Object> data,
      final String message) {
    final JsonNode before = snapshot(table.game);
    table.reject(0, command, payload(command, card, data), message);
    assertThat(snapshot(table.game)).isEqualTo(before);
  }
}
