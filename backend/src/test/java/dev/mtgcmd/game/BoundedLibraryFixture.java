package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

final class BoundedLibraryFixture {
  static final ObjectMapper MAPPER = new ObjectMapper();
  final LifecycleFixture table;
  final Game game;

  BoundedLibraryFixture(final GameFormat format) {
    table = new LifecycleFixture(format);
    table.startGame();
    game = table.game;
  }

  List<Game.Card> library(final int target) {
    return GameLookup.zone(game, target, "library");
  }

  List<String> ids(final int target) {
    return library(target).stream().map(card -> card.id).toList();
  }

  List<String> definitions(final int target) {
    return library(target).stream().map(card -> card.definition.id()).toList();
  }

  JsonNode view(final int seat) {
    return table.view(seat);
  }

  String open(final int actor, final int target, final String type, final int count) {
    table.apply(actor, type, Map.of("target", target, "count", count));
    final JsonNode session = view(actor).path("libraryInspection");
    assertThat(session.path("cardIds").size()).isEqualTo(Math.min(count, library(target).size()));
    return session.path("id").asText();
  }

  void commit(final int actor, final String id, final List<String> top, final List<String> bottom) {
    table.apply(actor, "APPLY_SCRY", Map.of("inspectionId", id, "top", top, "bottom", bottom));
  }

  void reject(final int seat, final String type, final Map<String, Object> data) {
    final JsonNode before = MAPPER.valueToTree(game);
    assertThatThrownBy(
            () ->
                table.engine.apply(
                    game,
                    seat,
                    new Game.Command(UUID.randomUUID().toString(), game.revision, type, data)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(MAPPER.<JsonNode>valueToTree(game)).isEqualTo(before);
  }
}
