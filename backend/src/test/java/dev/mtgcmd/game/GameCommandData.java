package dev.mtgcmd.game;

import java.util.*;

/** Command payloads and prerequisite objects for the command characterization matrix. */
final class GameCommandData {
  private final GameEngine engine;

  GameCommandData(GameEngine engine) {
    this.engine = engine;
  }

  private void apply(Game game, String type, Map<String, Object> data) {
    engine.apply(
        game, 0, new Game.Command(UUID.randomUUID().toString(), game.revision, type, data));
  }

  private Game.Card permanent(Game game) {
    Game.Card card =
        game.cards.stream()
            .filter(c -> c.owner == 0 && c.zone.equals("hand"))
            .findFirst()
            .orElseThrow();
    apply(game, "MOVE", Map.of("cardId", card.id, "zone", "battlefield"));
    return card;
  }

  Map<String, Object> forCommand(Game game, String type) {
    if (Set.of("READY", "GRANT", "COUNTER", "COMMANDER_CAST").contains(type)) {
      return playerData(game, type);
    }
    if (Set.of("MOVE", "TAP", "FACE_DOWN", "TRANSFORM", "REVEAL").contains(type)) {
      return cardStateData(game, type);
    }
    if (Set.of("CONTROL", "ATTACH", "POSITION", "COMBAT", "CARD_COUNTER").contains(type)) {
      return battlefieldData(game, type);
    }
    if (Set.of("TOKEN", "EDIT_TOKEN", "COPY", "ABILITY", "RESOLVE_ABILITY").contains(type)) {
      return objectData(game, type);
    }
    return Map.of();
  }

  private Map<String, Object> playerData(Game game, String type) {
    return switch (type) {
      case "READY" -> Map.of("acceptWarnings", true);
      case "GRANT" -> Map.of("target", 1);
      case "COUNTER" -> Map.of("name", "life", "delta", -1);
      case "COMMANDER_CAST" ->
          Map.of(
              "key",
              game.cards.stream()
                  .filter(card -> card.commanderKey != null && card.owner == 0)
                  .findFirst()
                  .orElseThrow()
                  .commanderKey);
      default -> throw new IllegalArgumentException("Unexpected player fixture: " + type);
    };
  }

  private Map<String, Object> cardStateData(Game game, String type) {
    return switch (type) {
      case "MOVE" -> Map.of("cardId", permanent(game).id, "zone", "graveyard");
      case "TAP", "FACE_DOWN" -> Map.of("cardId", permanent(game).id);
      case "TRANSFORM" -> transformData(game);
      case "REVEAL" -> Map.of("cardId", permanent(game).id, "audience", List.of(1));
      default -> throw new IllegalArgumentException("Unexpected card-state fixture: " + type);
    };
  }

  private Map<String, Object> transformData(Game game) {
    var card = permanent(game);
    card.definition =
        new Game.Definition(
            "faces",
            "Front // Back",
            "",
            "",
            "Creature",
            List.of(),
            true,
            List.of(
                new Game.Face("Front", "", "", "Creature"),
                new Game.Face("Back", "", "", "Creature")));
    return Map.of("cardId", card.id);
  }

  private Map<String, Object> battlefieldData(Game game, String type) {
    return switch (type) {
      case "CONTROL" -> Map.of("cardId", permanent(game).id, "target", 1);
      case "ATTACH" -> Map.of("cardId", permanent(game).id, "targetId", permanent(game).id);
      case "POSITION" -> Map.of("cardId", permanent(game).id, "x", .25, "y", .5);
      case "COMBAT" -> Map.of("cardId", permanent(game).id, "marker", "attacker");
      case "CARD_COUNTER" -> Map.of("cardId", permanent(game).id, "name", "charge", "delta", 1);
      default -> throw new IllegalArgumentException("Unexpected battlefield fixture: " + type);
    };
  }

  private Map<String, Object> objectData(Game game, String type) {
    return switch (type) {
      case "TOKEN" -> Map.of("name", "Zombie");
      case "COPY" -> Map.of("cardId", permanent(game).id);
      case "EDIT_TOKEN" -> {
        apply(game, "TOKEN", Map.of("name", "Zombie"));
        yield Map.of("cardId", game.cards.getLast().id, "name", "Skeleton");
      }
      case "ABILITY" -> Map.of("text", "Draw a card");
      case "RESOLVE_ABILITY" -> {
        apply(game, "ABILITY", Map.of("text", "Draw a card"));
        yield Map.of("abilityId", game.abilities.getFirst().id);
      }
      default -> throw new IllegalArgumentException("Unexpected object fixture: " + type);
    };
  }
}
