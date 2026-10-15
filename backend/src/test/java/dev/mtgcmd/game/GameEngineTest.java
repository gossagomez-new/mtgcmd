package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;

class GameEngineTest {
  private static final String DRAW_COMMAND = "DRAW";

  private static final String MOVE_COMMAND = "MOVE";

  private static final String START_COMMAND = "START";

  private static final String BATTLEFIELD_ZONE = "battlefield";

  private static final String CARD_ID_FIELD = "cardId";

  private static final String COUNT_FIELD = "count";

  private static final String HAND_ZONE = "hand";

  private static final String LIBRARY_ZONE = "library";

  private static final String TARGET_FIELD = "target";

  private static final String ZONE_FIELD = "zone";

  @Test
  void concealedCommanderMetadataDoesNotExposeIdentityAndZoneMovesResetPosition() {
    final Game game = started();
    final Game.Card card = card(game, 0, "command");
    action(
        game,
        0,
        MOVE_COMMAND,
        Map.of(CARD_ID_FIELD, card.id, ZONE_FIELD, BATTLEFIELD_ZONE, "faceDown", true));
    final JsonNode cards =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .valueToTree(engine.view(game, 1))
            .path("cards");
    final JsonNode hidden =
        java.util.stream.StreamSupport.stream(cards.spliterator(), false)
            .filter(node -> node.path("id").asText().equals(card.id))
            .findFirst()
            .orElseThrow();
    assertThat(hidden.path("definition").isMissingNode()).isTrue();
    assertThat(hidden.path("commanderKey").isNull()).isTrue();
    action(game, 0, "POSITION", Map.of(CARD_ID_FIELD, card.id, "x", 0.7, "y", 0.5));
    action(game, 0, MOVE_COMMAND, Map.of(CARD_ID_FIELD, card.id, ZONE_FIELD, "graveyard"));
    assertThat(card.x).isZero();
    assertThat(card.y).isZero();
  }

  @Test
  void concedingTheActiveSeatDoesNotStrandTheTurnMarker() {
    final Game game = started();
    action(game, 0, "CONCEDE", Map.of());
    assertThat(game.active).isEqualTo(1);
    action(game, 1, "PHASE", Map.of());
    assertThat(game.phase).isEqualTo("Upkeep");
  }

  final GameEngine engine = new GameEngine();

  static Game.Definition definition(final String name) {
    return new Game.Definition(
        name, name, "", "", "Basic Land — Island", List.of("U"), true, List.of());
  }

  Game lobby() {
    final Game game = new Game();
    for (int i = 0; i < 4; i++) {
      engine.join(game, "Player " + i);
      engine.load(game, i, Collections.nCopies(10, definition("Island")), List.of("Island"));
      action(game, i, "READY", Map.of("ready", true, "acceptWarnings", true));
    }
    return game;
  }

  Game started() {
    final Game game = lobby();
    action(game, 0, START_COMMAND, Map.of());
    return game;
  }

  void action(final Game game, final int seat, final String type, final Map<String, Object> data) {
    engine.apply(
        game, seat, new Game.Command(UUID.randomUUID().toString(), game.revision, type, data));
  }

  Game.Card card(final Game game, final int owner, final String zone) {
    return game.cards.stream()
        .filter(c -> c.zoneOwner == owner && c.zone.equals(zone))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void fourReadySeatsAndHostAreRequiredToStart() {
    final Game game = lobby();
    assertThatThrownBy(() -> engine.join(game, "Fifth")).hasMessageContaining("full");
    assertThatThrownBy(() -> action(game, 1, START_COMMAND, Map.of())).hasMessageContaining("host");
    game.players.get(3).ready = false;
    assertThatThrownBy(() -> action(game, 0, START_COMMAND, Map.of()))
        .hasMessageContaining("ready");
    game.players.get(3).ready = true;
    action(game, 0, START_COMMAND, Map.of());
    assertThat(game.status).isEqualTo("playing");
    assertThat(game.cards).hasSize(40);
    assertThat(game.cards.stream().filter(c -> HAND_ZONE.equals(Objects.requireNonNull(c.zone))))
        .hasSize(28);
  }

  @Test
  void privateViewsNeverContainOtherHandsOrLibraryIdentities() {
    final Game game = started();
    final Game.Card hand = card(game, 1, HAND_ZONE), library = card(game, 0, LIBRARY_ZONE);
    assertThat(engine.view(game, 0).toString()).doesNotContain(hand.id, library.id);
    assertThat(engine.view(game, 1).toString()).contains(hand.id);
    assertThatThrownBy(
            () ->
                action(
                    game,
                    0,
                    MOVE_COMMAND,
                    Map.of(CARD_ID_FIELD, hand.id, ZONE_FIELD, BATTLEFIELD_ZONE)))
        .hasMessageContaining("permission");
  }

  @Test
  void drawMovesExactlyOneCardAndShuffleChangesHiddenIdentifiers() {
    final Game game = started();
    final Set<String> ids = new HashSet<>();
    game.cards.stream()
        .filter(c -> LIBRARY_ZONE.equals(Objects.requireNonNull(c.zone)) && c.owner == 0)
        .forEach(c -> ids.add(c.id));
    action(game, 0, "SHUFFLE", Map.of());
    assertThat(
            game.cards.stream()
                .filter(c -> LIBRARY_ZONE.equals(Objects.requireNonNull(c.zone)) && c.owner == 0)
                .map(c -> c.id))
        .doesNotContainAnyElementsOf(ids);
    action(game, 0, DRAW_COMMAND, Map.of(COUNT_FIELD, 1));
    assertThat(
            game.cards.stream()
                .filter(c -> HAND_ZONE.equals(Objects.requireNonNull(c.zone)) && c.owner == 0))
        .hasSize(8);
    action(game, 0, DRAW_COMMAND, Map.of(COUNT_FIELD, 1));
    assertThatThrownBy(() -> action(game, 0, DRAW_COMMAND, Map.of(COUNT_FIELD, 1)))
        .hasMessageContaining(LIBRARY_ZONE);
  }

  @Test
  void searchesRevealsAndGrantsHaveExplicitAudiences() {
    final Game game = started();
    final String libraryId = card(game, 1, LIBRARY_ZONE).id;
    assertThatThrownBy(() -> action(game, 0, "SEARCH", Map.of(TARGET_FIELD, 1)))
        .hasMessageContaining("permission");
    action(game, 1, "GRANT", Map.of(TARGET_FIELD, 0, "allow", true));
    action(game, 0, "SEARCH", Map.of(TARGET_FIELD, 1));
    assertThat(engine.view(game, 0).toString()).contains(libraryId);
    assertThat(engine.view(game, 2).toString()).doesNotContain(libraryId);
    action(game, 0, "REVEAL", Map.of(CARD_ID_FIELD, libraryId, "audience", List.of(2)));
    assertThat(engine.view(game, 2).toString()).contains(libraryId);
    assertThat(engine.view(game, 3).toString()).doesNotContain(libraryId);
    action(game, 1, "GRANT", Map.of(TARGET_FIELD, 0, "allow", false));
    assertThat(engine.view(game, 0).toString()).doesNotContain(libraryId);
  }

  @Test
  void publicFaceDownCardsHideDefinitionButControllerCanInspect() {
    final Game game = started();
    final Game.Card card = card(game, 0, HAND_ZONE);
    card.definition = definition("Private Morph Name");
    action(
        game,
        0,
        MOVE_COMMAND,
        Map.of(CARD_ID_FIELD, card.id, ZONE_FIELD, BATTLEFIELD_ZONE, "faceDown", true));
    assertThat(engine.view(game, 1).toString()).doesNotContain("Private Morph Name");
    assertThat(engine.view(game, 0).toString()).contains("Private Morph Name");
  }

  @Test
  void controlTransfersDoNotChangeOwnershipAndZoneMovesClearTransientState() {
    final Game game = started();
    final Game.Card card = card(game, 0, HAND_ZONE);
    action(game, 0, MOVE_COMMAND, Map.of(CARD_ID_FIELD, card.id, ZONE_FIELD, BATTLEFIELD_ZONE));
    action(game, 0, "CONTROL", Map.of(CARD_ID_FIELD, card.id, TARGET_FIELD, 1));
    assertThat(card.owner).isZero();
    assertThat(card.controller).isEqualTo(1);
    assertThatThrownBy(() -> action(game, 0, "TAP", Map.of(CARD_ID_FIELD, card.id)))
        .hasMessageContaining("permission");
    action(game, 1, "TAP", Map.of(CARD_ID_FIELD, card.id));
    action(game, 1, "CARD_COUNTER", Map.of(CARD_ID_FIELD, card.id, "name", "+1/+1", "delta", 2));
    action(game, 1, MOVE_COMMAND, Map.of(CARD_ID_FIELD, card.id, ZONE_FIELD, "graveyard"));
    assertThat(card.owner).isZero();
    assertThat(card.zoneOwner).isZero();
    assertThat(card.tapped).isFalse();
    assertThat(card.counters).isEmpty();
  }

  @Test
  void tokensCopiesAttachmentsAndManualStackAreSupported() {
    final Game game = started();
    action(game, 0, "TOKEN", Map.of("name", "Zombie", "type", "Creature — Zombie", "text", "2/2"));
    final Game.Card token = game.cards.getLast();
    action(game, 0, "COPY", Map.of(CARD_ID_FIELD, token.id));
    final Game.Card copy = game.cards.getLast();
    action(game, 0, "ATTACH", Map.of(CARD_ID_FIELD, copy.id, "targetId", token.id));
    assertThat(copy.attachedTo).isEqualTo(token.id);
    action(game, 0, "ABILITY", Map.of("text", "Draw a card"));
    assertThat(game.abilities).hasSize(1);
    action(game, 0, "RESOLVE_ABILITY", Map.of("abilityId", game.abilities.getFirst().id));
    assertThat(game.abilities).isEmpty();
    action(game, 0, MOVE_COMMAND, Map.of(CARD_ID_FIELD, token.id, ZONE_FIELD, "graveyard"));
    assertThat(game.cards).doesNotContain(token);
    assertThat(copy.attachedTo).isNull();
  }

  @Test
  void malformedCommandsAndUnseenLibraryIdsCannotBeUsed() {
    final Game game = started();
    final String identifier = card(game, 0, LIBRARY_ZONE).id;
    assertThatThrownBy(
            () ->
                action(
                    game,
                    0,
                    MOVE_COMMAND,
                    Map.of(CARD_ID_FIELD, identifier, ZONE_FIELD, HAND_ZONE)))
        .hasMessageContaining("search");
    assertThatThrownBy(() -> action(game, 0, DRAW_COMMAND, Map.of(COUNT_FIELD, -1)))
        .hasMessageContaining(COUNT_FIELD);
    assertThatThrownBy(() -> action(game, 0, "COUNTER", Map.of("name", "life", "delta", "oops")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> action(game, 0, "UNKNOWN", Map.of())).hasMessageContaining("Unknown");
  }
}
