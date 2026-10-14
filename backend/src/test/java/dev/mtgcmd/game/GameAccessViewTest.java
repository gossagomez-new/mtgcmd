package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class GameAccessViewTest {
  private final GameAccess access = new GameAccess();
  private final GameEngineCommandsTest fixtures = new GameEngineCommandsTest();

  private Game.Card card(Game game, int owner, String zone) {
    return game.cards.stream()
        .filter(c -> c.owner == owner && c.zone.equals(zone))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void grantsSearchAndRevealHaveDistinctVisibilityAndEditingEffects() {
    Game game = fixtures.playing(GameFormat.COMMANDER);
    Game.Card library = card(game, 1, "library");
    assertThat(access.visible(game, 0, library)).isFalse();
    assertThat(access.mayEdit(game, 0, library)).isFalse();
    game.grants.put(1, new HashSet<>(Set.of(0)));
    assertThat(access.visible(game, 0, library)).isFalse();
    assertThatThrownBy(() -> access.editable(game, 0, Map.of("cardId", library.id)))
        .hasMessage("Open a library search first");
    game.searches.put(0, 1);
    assertThat(access.visible(game, 0, library)).isTrue();
    assertThat(access.editable(game, 0, Map.of("cardId", library.id))).isSameAs(library);
    game.grants.clear();
    library.revealedTo.add(0);
    assertThat(access.canSeeDefinition(game, 0, library)).isTrue();
    assertThat(access.mayEdit(game, 0, library)).isFalse();
  }

  @Test
  void publicFaceDownCardIsVisibleButInspectionAndEditingDependOnDifferentPolicies() {
    Game game = fixtures.playing(GameFormat.STANDARD);
    Game.Card permanent = card(game, 0, "hand");
    permanent.zone = "battlefield";
    permanent.faceDown = true;
    assertThat(access.visible(game, 1, permanent)).isTrue();
    assertThat(access.canSeeDefinition(game, 1, permanent)).isFalse();
    assertThat(access.canSeeDefinition(game, 0, permanent)).isTrue();
    permanent.revealedTo.add(1);
    assertThat(access.canSeeDefinition(game, 1, permanent)).isTrue();
    assertThat(access.mayEdit(game, 1, permanent)).isFalse();
    game.players.get(0).conceded = true;
    assertThat(access.mayEdit(game, 0, permanent)).isFalse();
  }

  @Test
  void projectionPreservesWireFieldsAndDoesNotMutateTheGame() {
    Game game = fixtures.playing(GameFormat.COMMANDER);
    Game.Card hand = card(game, 1, "hand");
    Game.Card permanent = card(game, 0, "command");
    permanent.zone = "battlefield";
    permanent.faceDown = true;
    var mapper = new ObjectMapper();
    var before = mapper.valueToTree(game);
    var views = new GameViewMapper(access);
    Map<String, Object> view = views.forPlayer(game, 1);
    assertThat(view.keySet())
        .containsExactlyInAnyOrder(
            "id",
            "revision",
            "status",
            "format",
            "seatCapacity",
            "host",
            "active",
            "turn",
            "phase",
            "you",
            "grants",
            "search",
            "log",
            "players",
            "cards",
            "abilities");
    var json = mapper.valueToTree(view);
    assertThat(json.path("cards").toString()).contains(hand.id);
    assertThat(views.forPlayer(game, 2).toString()).doesNotContain(hand.id);
    var hidden =
        java.util.stream.StreamSupport.stream(json.path("cards").spliterator(), false)
            .filter(node -> node.path("id").asText().equals(permanent.id))
            .findFirst()
            .orElseThrow();
    assertThat(hidden.has("definition")).isFalse();
    assertThat(hidden.path("commanderKey").isNull()).isTrue();
    assertThat(hidden.path("editable").asBoolean()).isFalse();
    assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(game)).isEqualTo(before);
    assertThatThrownBy(() -> views.forPlayer(game, -1)).hasMessage("Unknown seat");
  }
}
