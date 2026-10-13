package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class GameEngineTest {
  @Test
  void concealedCommanderMetadataDoesNotExposeIdentityAndZoneMovesResetPosition() {
    Game g = started();
    Game.Card c = card(g, 0, "command");
    action(g, 0, "MOVE", Map.of("cardId", c.id, "zone", "battlefield", "faceDown", true));
    var cards =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .valueToTree(engine.view(g, 1))
            .path("cards");
    var hidden =
        java.util.stream.StreamSupport.stream(cards.spliterator(), false)
            .filter(node -> node.path("id").asText().equals(c.id))
            .findFirst()
            .orElseThrow();
    assertThat(hidden.path("definition").isMissingNode()).isTrue();
    assertThat(hidden.path("commanderKey").isNull()).isTrue();
    action(g, 0, "POSITION", Map.of("cardId", c.id, "x", 0.7, "y", 0.5));
    action(g, 0, "MOVE", Map.of("cardId", c.id, "zone", "graveyard"));
    assertThat(c.x).isZero();
    assertThat(c.y).isZero();
  }

  @Test
  void concedingTheActiveSeatDoesNotStrandTheTurnMarker() {
    Game g = started();
    action(g, 0, "CONCEDE", Map.of());
    assertThat(g.active).isEqualTo(1);
    action(g, 1, "PHASE", Map.of());
    assertThat(g.phase).isEqualTo("Upkeep");
  }

  final GameEngine engine = new GameEngine();

  static Game.Definition definition(String name) {
    return new Game.Definition(
        name, name, "", "", "Basic Land — Island", List.of("U"), true, List.of());
  }

  Game lobby() {
    Game g = new Game();
    for (int i = 0; i < 4; i++) {
      engine.join(g, "Player " + i);
      engine.load(g, i, Collections.nCopies(10, definition("Island")), List.of("Island"));
      action(g, i, "READY", Map.of("ready", true, "acceptWarnings", true));
    }
    return g;
  }

  Game started() {
    Game g = lobby();
    action(g, 0, "START", Map.of());
    return g;
  }

  void action(Game g, int seat, String type, Map<String, Object> data) {
    engine.apply(g, seat, new Game.Command(UUID.randomUUID().toString(), g.revision, type, data));
  }

  Game.Card card(Game g, int owner, String zone) {
    return g.cards.stream()
        .filter(c -> c.zoneOwner == owner && c.zone.equals(zone))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void fourReadySeatsAndHostAreRequiredToStart() {
    Game g = lobby();
    assertThatThrownBy(() -> engine.join(g, "Fifth")).hasMessageContaining("full");
    assertThatThrownBy(() -> action(g, 1, "START", Map.of())).hasMessageContaining("host");
    g.players.get(3).ready = false;
    assertThatThrownBy(() -> action(g, 0, "START", Map.of())).hasMessageContaining("ready");
    g.players.get(3).ready = true;
    action(g, 0, "START", Map.of());
    assertThat(g.status).isEqualTo("playing");
    assertThat(g.cards).hasSize(40);
    assertThat(g.cards.stream().filter(c -> c.zone.equals("hand"))).hasSize(28);
  }

  @Test
  void privateViewsNeverContainOtherHandsOrLibraryIdentities() {
    Game g = started();
    Game.Card hand = card(g, 1, "hand"), library = card(g, 0, "library");
    assertThat(engine.view(g, 0).toString()).doesNotContain(hand.id, library.id);
    assertThat(engine.view(g, 1).toString()).contains(hand.id);
    assertThatThrownBy(() -> action(g, 0, "MOVE", Map.of("cardId", hand.id, "zone", "battlefield")))
        .hasMessageContaining("permission");
  }

  @Test
  void drawMovesExactlyOneCardAndShuffleChangesHiddenIdentifiers() {
    Game g = started();
    Set<String> ids = new HashSet<>();
    g.cards.stream()
        .filter(c -> c.zone.equals("library") && c.owner == 0)
        .forEach(c -> ids.add(c.id));
    action(g, 0, "SHUFFLE", Map.of());
    assertThat(
            g.cards.stream().filter(c -> c.zone.equals("library") && c.owner == 0).map(c -> c.id))
        .doesNotContainAnyElementsOf(ids);
    action(g, 0, "DRAW", Map.of("count", 1));
    assertThat(g.cards.stream().filter(c -> c.zone.equals("hand") && c.owner == 0)).hasSize(8);
    action(g, 0, "DRAW", Map.of("count", 1));
    assertThatThrownBy(() -> action(g, 0, "DRAW", Map.of("count", 1)))
        .hasMessageContaining("library");
  }

  @Test
  void searchesRevealsAndGrantsHaveExplicitAudiences() {
    Game g = started();
    String libraryId = card(g, 1, "library").id;
    assertThatThrownBy(() -> action(g, 0, "SEARCH", Map.of("target", 1)))
        .hasMessageContaining("permission");
    action(g, 1, "GRANT", Map.of("target", 0, "allow", true));
    action(g, 0, "SEARCH", Map.of("target", 1));
    assertThat(engine.view(g, 0).toString()).contains(libraryId);
    assertThat(engine.view(g, 2).toString()).doesNotContain(libraryId);
    action(g, 0, "REVEAL", Map.of("cardId", libraryId, "audience", List.of(2)));
    assertThat(engine.view(g, 2).toString()).contains(libraryId);
    assertThat(engine.view(g, 3).toString()).doesNotContain(libraryId);
    action(g, 1, "GRANT", Map.of("target", 0, "allow", false));
    assertThat(engine.view(g, 0).toString()).doesNotContain(libraryId);
  }

  @Test
  void publicFaceDownCardsHideDefinitionButControllerCanInspect() {
    Game g = started();
    Game.Card c = card(g, 0, "hand");
    c.definition = definition("Private Morph Name");
    action(g, 0, "MOVE", Map.of("cardId", c.id, "zone", "battlefield", "faceDown", true));
    assertThat(engine.view(g, 1).toString()).doesNotContain("Private Morph Name");
    assertThat(engine.view(g, 0).toString()).contains("Private Morph Name");
  }

  @Test
  void controlTransfersDoNotChangeOwnershipAndZoneMovesClearTransientState() {
    Game g = started();
    Game.Card c = card(g, 0, "hand");
    action(g, 0, "MOVE", Map.of("cardId", c.id, "zone", "battlefield"));
    action(g, 0, "CONTROL", Map.of("cardId", c.id, "target", 1));
    assertThat(c.owner).isZero();
    assertThat(c.controller).isEqualTo(1);
    assertThatThrownBy(() -> action(g, 0, "TAP", Map.of("cardId", c.id)))
        .hasMessageContaining("permission");
    action(g, 1, "TAP", Map.of("cardId", c.id));
    action(g, 1, "CARD_COUNTER", Map.of("cardId", c.id, "name", "+1/+1", "delta", 2));
    action(g, 1, "MOVE", Map.of("cardId", c.id, "zone", "graveyard"));
    assertThat(c.owner).isZero();
    assertThat(c.zoneOwner).isZero();
    assertThat(c.tapped).isFalse();
    assertThat(c.counters).isEmpty();
  }

  @Test
  void tokensCopiesAttachmentsAndManualStackAreSupported() {
    Game g = started();
    action(g, 0, "TOKEN", Map.of("name", "Zombie", "type", "Creature — Zombie", "text", "2/2"));
    Game.Card token = g.cards.getLast();
    action(g, 0, "COPY", Map.of("cardId", token.id));
    Game.Card copy = g.cards.getLast();
    action(g, 0, "ATTACH", Map.of("cardId", copy.id, "targetId", token.id));
    assertThat(copy.attachedTo).isEqualTo(token.id);
    action(g, 0, "ABILITY", Map.of("text", "Draw a card"));
    assertThat(g.abilities).hasSize(1);
    action(g, 0, "RESOLVE_ABILITY", Map.of("abilityId", g.abilities.getFirst().id));
    assertThat(g.abilities).isEmpty();
    action(g, 0, "MOVE", Map.of("cardId", token.id, "zone", "graveyard"));
    assertThat(g.cards).doesNotContain(token);
    assertThat(copy.attachedTo).isNull();
  }

  @Test
  void malformedCommandsAndUnseenLibraryIdsCannotBeUsed() {
    Game g = started();
    String id = card(g, 0, "library").id;
    assertThatThrownBy(() -> action(g, 0, "MOVE", Map.of("cardId", id, "zone", "hand")))
        .hasMessageContaining("search");
    assertThatThrownBy(() -> action(g, 0, "DRAW", Map.of("count", -1)))
        .hasMessageContaining("count");
    assertThatThrownBy(() -> action(g, 0, "COUNTER", Map.of("name", "life", "delta", "oops")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> action(g, 0, "UNKNOWN", Map.of())).hasMessageContaining("Unknown");
  }
}
