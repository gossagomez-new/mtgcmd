package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ObjectActionsTest {
  private static final String TOKEN = "TOKEN";
  private static final String EDIT_TOKEN = "EDIT_TOKEN";
  private static final String COPY = "COPY";
  private static final String ABILITY = "ABILITY";
  private static final String RESOLVE = "RESOLVE_ABILITY";
  private static final String NAME = "name";
  private static final String TEXT = "text";
  private static final String TYPE = "type";
  private static final String CARD_ID = "cardId";
  private static final String ABILITY_ID = "abilityId";
  private static final String CREATURE = "Creature";
  private static final String GROWTH = "growth";
  private static final String ZOMBIE = "Zombie";
  private static final String UNKNOWN_CARD = "missing";
  private static final String FULL_TABLE = "Table has too many objects";
  private static final String FULL_STACK = "Stack is full";
  private static final String NO_INSPECTION = "No permission to inspect this card";
  private static final String NO_EDIT = "No permission to change this card";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void tokensHaveActorOwnershipUniqueIdentityAndExplicitOrDefaultCharacteristics(
      final GameFormat format) {
    final LifecycleFixture table = playing(format);
    final int count = table.game.cards.size();
    table.apply(1, TOKEN, Map.of(NAME, " Zombie ", TEXT, " 2/2 ", TYPE, " Creature "));
    final Game.Card first = table.game.cards.getLast();
    assertThat(table.game.cards).hasSize(count + 1);
    assertToken(first, 1);
    assertThat(first.definition.name()).isEqualTo(ZOMBIE);
    assertThat(first.definition.text()).isEqualTo("2/2");
    assertThat(first.definition.type()).isEqualTo(CREATURE);
    assertThat(first.definition.image()).isEmpty();
    assertThat(first.definition.colors()).isEmpty();
    assertThat(first.definition.faces()).isEmpty();
    assertThat(first.definition.legal()).isTrue();
    table.apply(1, TOKEN, Map.of(NAME, ZOMBIE));
    final Game.Card second = table.game.cards.getLast();
    assertToken(second, 1);
    assertThat(second.id).isNotEqualTo(first.id);
    assertThat(second.definition.id()).isNotBlank().isNotEqualTo(first.definition.id());
    assertThat(second.definition.text()).isEmpty();
    assertThat(second.definition.type()).isEmpty();
    first.counters.put(GROWTH, 3);
    assertThat(second.counters).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void editingTokenChangesCharacteristicsWithoutResettingPermanentState(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    table.apply(0, TOKEN, Map.of(NAME, ZOMBIE));
    final Game.Card card = table.game.cards.getLast();
    card.definition =
        new Game.Definition(
            "token-definition", ZOMBIE, "art", "old", CREATURE, List.of("G"), true, List.of());
    card.owner = 1;
    card.zoneOwner = 1;
    card.tapped = true;
    card.counters.put(GROWTH, 2);
    card.attachedTo = table.game.cards.getFirst().id;
    final JsonNode state = permanentState(card);
    table.apply(
        0,
        EDIT_TOKEN,
        Map.of(CARD_ID, card.id, NAME, " Spirit ", TEXT, " Flying ", TYPE, " Creature — Spirit "));
    assertThat(permanentState(card)).isEqualTo(state);
    assertThat(card.definition.id()).isEqualTo("token-definition");
    assertThat(card.definition.image()).isEqualTo("art");
    assertThat(card.definition.name()).isEqualTo("Spirit");
    assertThat(card.definition.text()).isEqualTo("Flying");
    assertThat(card.definition.type()).isEqualTo("Creature — Spirit");
    assertThat(card.definition.colors()).isEmpty();
    assertThat(card.definition.faces()).isEmpty();
    table.apply(0, EDIT_TOKEN, Map.of(CARD_ID, card.id, NAME, ZOMBIE));
    assertThat(card.definition.text()).isEmpty();
    assertThat(card.definition.type()).isEmpty();
    assertThat(permanentState(card)).isEqualTo(state);
    card.controller = 1;
    reject(table, 0, EDIT_TOKEN, Map.of(CARD_ID, card.id, NAME, ZOMBIE), NO_EDIT);
    card.controller = 0;
    card.token = false;
    reject(
        table, 0, EDIT_TOKEN, Map.of(CARD_ID, card.id), "Only token characteristics can be edited");
    reject(table, 0, EDIT_TOKEN, Map.of(CARD_ID, UNKNOWN_CARD, NAME, ZOMBIE), "Card unavailable");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void privateTokenEditingRequiresCurrentGrantAndLibrarySearch(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    table.apply(1, TOKEN, Map.of(NAME, ZOMBIE));
    final Game.Card card = table.game.cards.getLast();
    card.zone = GameZones.HAND;
    final Map<String, Object> data = Map.of(CARD_ID, card.id, NAME, "Spirit");
    reject(table, 0, EDIT_TOKEN, data, NO_EDIT);
    table.game.grants.put(1, Set.of(0));
    table.apply(0, EDIT_TOKEN, data);
    assertThat(card.definition.name()).isEqualTo("Spirit");
    assertThat(card.owner).isEqualTo(1);
    assertThat(card.controller).isEqualTo(1);
    card.zone = GameZones.LIBRARY;
    reject(table, 0, EDIT_TOKEN, data, "Open a library search first");
    table.game.searches.put(0, 1);
    table.apply(0, EDIT_TOKEN, Map.of(CARD_ID, card.id, NAME, ZOMBIE));
    assertThat(card.definition.name()).isEqualTo(ZOMBIE);
    table.game.grants.clear();
    reject(table, 0, EDIT_TOKEN, data, NO_EDIT);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void copyingPublicCardsPreservesDefinitionAndFaceButCreatesIndependentTokenState(
      final GameFormat format) {
    final LifecycleFixture table = playing(format);
    final Game.Card source = table.game.cards.getFirst();
    source.owner = 1;
    source.zoneOwner = 1;
    source.controller = 1;
    source.definition =
        new Game.Definition(
            "double",
            "Front // Back",
            "art",
            "rules",
            CREATURE,
            List.of("U"),
            true,
            List.of(
                new Game.Face("Front", "front", "", CREATURE),
                new Game.Face("Back", "back", "", CREATURE)));
    source.face = 1;
    source.tapped = true;
    source.counters.put(GROWTH, 3);
    source.combat = "attacking";
    source.attachedTo = table.game.cards.get(1).id;
    source.x = 0.5;
    source.y = 0.5;
    source.stackOrder = 17;
    for (final String zone :
        List.of(
            GameZones.BATTLEFIELD,
            GameZones.GRAVEYARD,
            GameZones.EXILE,
            GameZones.COMMAND,
            GameZones.STACK)) {
      if (!format.usesCommanders() && GameZones.COMMAND.equals(zone)) {
        continue;
      }
      source.zone = zone;
      final JsonNode before = snapshot(source);
      final int count = table.game.cards.size();
      table.apply(0, COPY, Map.of(CARD_ID, source.id));
      final Game.Card copy = table.game.cards.getLast();
      assertThat(table.game.cards).hasSize(count + 1);
      assertToken(copy, 0);
      assertThat(copy.id).isNotEqualTo(source.id);
      assertThat(copy.definition).isEqualTo(source.definition);
      assertThat(copy.face).isEqualTo(1);
      assertThat(copy.commanderKey).isNull();
      assertThat(copy.attachedTo).isNull();
      assertThat(copy.combat).isEmpty();
      assertThat(copy.stackOrder).isZero();
      assertThat(copy.x).isZero();
      assertThat(copy.y).isZero();
      assertThat(snapshot(source)).isEqualTo(before);
      copy.counters.put("new", 1);
      copy.revealedTo.add(0);
      assertThat(source.counters).containsExactlyEntriesOf(Map.of(GROWTH, 3));
      assertThat(source.revealedTo).isEmpty();
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void copyPermissionsDistinguishDefinitionVisibilityFromPublicZone(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    final Game.Card card = table.game.cards.getFirst();
    card.zoneOwner = 1;
    card.controller = 1;
    for (final String zone : List.of(GameZones.HAND, GameZones.LIBRARY)) {
      card.zone = zone;
      reject(table, 0, COPY, Map.of(CARD_ID, card.id), NO_INSPECTION);
      table.game.grants.put(1, Set.of(0));
      table.game.searches.put(0, 1);
      reject(table, 0, COPY, Map.of(CARD_ID, card.id), "Copy a public card");
      table.game.grants.clear();
      table.game.searches.clear();
      card.revealedTo.add(0);
      reject(table, 0, COPY, Map.of(CARD_ID, card.id), "Copy a public card");
      card.revealedTo.clear();
    }
    card.zone = GameZones.BATTLEFIELD;
    card.faceDown = true;
    reject(table, 0, COPY, Map.of(CARD_ID, card.id), NO_INSPECTION);
    card.revealedTo.add(0);
    table.apply(0, COPY, Map.of(CARD_ID, card.id));
    assertThat(table.game.cards.getLast().faceDown).isFalse();
    card.revealedTo.clear();
    card.controller = 0;
    table.apply(0, COPY, Map.of(CARD_ID, card.id));
    assertThat(table.game.cards.getLast().definition).isEqualTo(card.definition);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void objectCapacityAcceptsLastSlotAndRejectsFurtherCreationAtomically(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    final Game.Card source = table.game.cards.getFirst();
    source.zone = GameZones.BATTLEFIELD;
    while (table.game.cards.size() < 1999) {
      final Game.Card card = new Game.Card();
      card.definition = source.definition;
      card.zone = GameZones.BATTLEFIELD;
      table.game.cards.add(card);
    }
    table.apply(0, TOKEN, Map.of(NAME, ZOMBIE));
    assertThat(table.game.cards).hasSize(2000);
    reject(table, 0, TOKEN, Map.of(NAME, ZOMBIE), FULL_TABLE);
    reject(table, 0, COPY, Map.of(CARD_ID, source.id), FULL_TABLE);
    reject(table, 0, TOKEN, Map.of(), "Invalid name");
    source.faceDown = true;
    source.controller = 1;
    reject(table, 0, COPY, Map.of(CARD_ID, source.id), NO_INSPECTION);
    source.faceDown = false;
    table.game.cards.removeLast();
    table.apply(0, COPY, Map.of(CARD_ID, source.id));
    assertThat(table.game.cards).hasSize(2000);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void abilitiesPreserveControllerTextOrderAndResolveOnlySelectedInstance(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    final long revision = table.game.revision;
    table.apply(1, ABILITY, Map.of(TEXT, " Draw a card "));
    final Game.Ability first = table.game.abilities.getFirst();
    assertThat(first.id).isNotBlank();
    assertThat(first.controller).isEqualTo(1);
    assertThat(first.text).isEqualTo("Draw a card");
    assertThat(first.stackOrder).isEqualTo(revision + 1);
    table.apply(0, ABILITY, Map.of(TEXT, "Draw a card"));
    final Game.Ability second = table.game.abilities.getLast();
    assertThat(second.id).isNotEqualTo(first.id);
    assertThat(second.stackOrder).isEqualTo(revision + 2);
    assertThat(second.controller).isZero();
    reject(
        table, 0, RESOLVE, Map.of(ABILITY_ID, first.id), "No permission to resolve this ability");
    reject(table, 0, RESOLVE, Map.of(ABILITY_ID, UNKNOWN_CARD), "Unknown ability");
    table.apply(1, RESOLVE, Map.of(ABILITY_ID, first.id));
    assertThat(table.game.abilities).containsExactly(second);
    reject(table, 1, RESOLVE, Map.of(ABILITY_ID, first.id), "Unknown ability");
    table.apply(0, RESOLVE, Map.of(ABILITY_ID, second.id));
    assertThat(table.game.abilities).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void stackCapacityAndInputValidationPreserveStateAndPrecedence(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    for (final Object text : List.of("", " ", "x".repeat(501), 4)) {
      reject(table, 0, ABILITY, Map.of(TEXT, text), "Invalid text");
    }
    reject(table, 0, ABILITY, Map.of(), "Invalid text");
    table.apply(0, ABILITY, Map.of(TEXT, "x".repeat(500)));
    while (table.game.abilities.size() < 199) {
      final Game.Ability ability = new Game.Ability();
      ability.text = "existing";
      table.game.abilities.add(ability);
    }
    table.apply(0, ABILITY, Map.of(TEXT, "last"));
    assertThat(table.game.abilities).hasSize(200);
    reject(table, 0, ABILITY, Map.of(TEXT, "overflow"), FULL_STACK);
    reject(table, 0, ABILITY, Map.of(), FULL_STACK);
    for (final Object identifier : List.of("", "x".repeat(81), 4)) {
      reject(table, 0, RESOLVE, Map.of(ABILITY_ID, identifier), "Invalid abilityId");
    }
    reject(table, 0, RESOLVE, Map.of(), "Invalid abilityId");
    final Game.Ability last = table.game.abilities.getLast();
    table.apply(0, RESOLVE, Map.of(ABILITY_ID, last.id));
    table.apply(0, ABILITY, Map.of(TEXT, "replacement"));
    assertThat(table.game.abilities).hasSize(200);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void tokenInputValidationAndFacadeAdmissionAreAtomic(final GameFormat format) {
    final LifecycleFixture table = playing(format);
    table.apply(0, TOKEN, Map.of(NAME, ZOMBIE));
    final Game.Card card = table.game.cards.getLast();
    for (final String command : List.of(TOKEN, EDIT_TOKEN)) {
      for (final Object name : List.of("", " ", "x".repeat(101), 4)) {
        reject(table, 0, command, tokenData(command, card, Map.of(NAME, name)), "Invalid name");
      }
      for (final String field : List.of(TEXT, TYPE)) {
        final int limit = TEXT.equals(field) ? 2000 : 200;
        for (final Object value : List.of("x".repeat(limit + 1), 4)) {
          reject(
              table,
              0,
              command,
              tokenData(command, card, Map.of(NAME, ZOMBIE, field, value)),
              "Invalid " + field);
        }
      }
      table.apply(
          0,
          command,
          tokenData(
              command,
              card,
              Map.of(NAME, "x".repeat(100), TEXT, "x".repeat(2000), TYPE, "x".repeat(200))));
    }
    for (final String command : List.of(TOKEN, EDIT_TOKEN, COPY, ABILITY, RESOLVE)) {
      reject(table, -1, command, Map.of(), "Unknown seat");
      table.game.players.getFirst().conceded = true;
      reject(table, 0, command, Map.of(), "You have conceded");
      table.game.players.getFirst().conceded = false;
      table.game.status = "lobby";
      reject(table, 0, command, Map.of(), "Game is not playing");
      table.game.status = "playing";
    }
    for (final String command : List.of(COPY, EDIT_TOKEN)) {
      reject(table, 0, command, Map.of(), "Invalid cardId");
      reject(table, 0, command, Map.of(CARD_ID, UNKNOWN_CARD), "Card unavailable");
    }
  }

  private static void assertToken(final Game.Card card, final int seat) {
    assertThat(card.id).isNotBlank();
    assertThat(card.token).isTrue();
    assertThat(card.owner).isEqualTo(seat);
    assertThat(card.controller).isEqualTo(seat);
    assertThat(card.zoneOwner).isEqualTo(seat);
    assertThat(card.zone).isEqualTo(GameZones.BATTLEFIELD);
    assertThat(card.tapped).isFalse();
    assertThat(card.faceDown).isFalse();
    assertThat(card.counters).isEmpty();
    assertThat(card.revealedTo).isEmpty();
  }

  private static LifecycleFixture playing(final GameFormat format) {
    final LifecycleFixture table = new LifecycleFixture(format);
    table.startGame();
    return table;
  }

  private static JsonNode snapshot(final Object value) {
    return MAPPER.valueToTree(value);
  }

  private static JsonNode permanentState(final Game.Card card) {
    final ObjectNode state = MAPPER.valueToTree(card);
    state.remove("definition");
    return state;
  }

  private static Map<String, Object> tokenData(
      final String command, final Game.Card card, final Map<String, Object> data) {
    final Map<String, Object> result = new HashMap<>(data);
    if (EDIT_TOKEN.equals(command)) {
      result.put(CARD_ID, card.id);
    }
    return result;
  }

  private static void reject(
      final LifecycleFixture table,
      final int seat,
      final String command,
      final Map<String, Object> data,
      final String message) {
    final JsonNode before = snapshot(table.game);
    table.reject(seat, command, data, message);
    assertThat(snapshot(table.game)).isEqualTo(before);
  }
}
