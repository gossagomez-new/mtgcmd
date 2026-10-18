package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Small valid playing snapshots; commands still enter through the public engine facade. */
final class PermanentActionFixture {
  static final String CARD_ID = "cardId";
  static final String TARGET_ID = "targetId";
  static final String TARGET = "target";
  static final String ZONE = "zone";
  static final String MARKER = "marker";
  static final String FACE_DOWN = "faceDown";
  static final String ATTACKER = "attacker";
  static final String BLOCKS = "blocks:";
  static final String AUDIENCE = "audience";
  static final String BATTLEFIELD_ERROR = "Card must be on the battlefield";
  static final String PERMISSION_ERROR = "No permission to change this card";
  final GameEngine engine = new GameEngine();
  final Game game = new Game();

  PermanentActionFixture(final GameFormat format) {
    game.format = format;
    for (int seat = 0; seat < format.seatCapacity(); seat++) {
      engine.join(game, "Player " + seat);
    }
    game.status = "playing";
  }

  Game.Card card(final int owner, final String zone) {
    final Game.Card card = new Game.Card();
    card.owner = owner;
    card.controller = owner;
    card.zoneOwner = owner;
    card.zone = zone;
    card.definition = definition(0);
    game.cards.add(card);
    return card;
  }

  static Game.Definition definition(final int faces) {
    final List<Game.Face> definitions = new ArrayList<>();
    for (int face = 0; face < faces; face++) {
      definitions.add(new Game.Face("Face " + face, "", "", "Creature"));
    }
    final String identifier = UUID.randomUUID().toString();
    return new Game.Definition(
        identifier, "Private card " + identifier, "", "", "Creature", List.of(), true, definitions);
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

  JsonNode viewCard(final int viewer, final Game.Card card) {
    final JsonNode cards = new ObjectMapper().valueToTree(engine.view(game, viewer)).path("cards");
    for (final JsonNode candidate : cards) {
      if (candidate.path("id").asText().equals(card.id)) {
        return candidate;
      }
    }
    throw new AssertionError("Expected visible card " + card.id);
  }
}
