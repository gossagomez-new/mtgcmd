package dev.mtgcmd.game;

import java.util.*;

/** Command payloads and prerequisite objects for the command characterization matrix. */
final class GameCommandData {
  private static final String CARD_ID_FIELD = "cardId";

  private static final String NAME_FIELD = "name";

  private final GameEngine engine;

  GameCommandData(final GameEngine engine) {
    this.engine = engine;
  }

  private void apply(final Game game, final String type, final Map<String, Object> data) {
    engine.apply(
        game, 0, new Game.Command(UUID.randomUUID().toString(), game.revision, type, data));
  }

  private Game.Card permanent(final Game game) {
    final Game.Card card =
        game.cards.stream()
            .filter(c -> c.owner == 0 && "hand".equals(Objects.requireNonNull(c.zone)))
            .findFirst()
            .orElseThrow();
    apply(game, "MOVE", Map.of(CARD_ID_FIELD, card.id, "zone", "battlefield"));
    return card;
  }

  Map<String, Object> forCommand(final Game game, final String type) {
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

  private Map<String, Object> playerData(final Game game, final String type) {
    return switch (type) {
      case "READY" -> Map.of("acceptWarnings", true);
      case "GRANT" -> Map.of("target", 1);
      case "COUNTER" -> Map.of(NAME_FIELD, "life", "delta", -1);
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

  private Map<String, Object> cardStateData(final Game game, final String type) {
    return switch (type) {
      case "MOVE" -> Map.of(CARD_ID_FIELD, permanent(game).id, "zone", "graveyard");
      case "TAP", "FACE_DOWN" -> Map.of(CARD_ID_FIELD, permanent(game).id);
      case "TRANSFORM" -> transformData(game);
      case "REVEAL" -> Map.of(CARD_ID_FIELD, permanent(game).id, "audience", List.of(1));
      default -> throw new IllegalArgumentException("Unexpected card-state fixture: " + type);
    };
  }

  private Map<String, Object> transformData(final Game game) {
    final Game.Card card = permanent(game);
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
    return Map.of(CARD_ID_FIELD, card.id);
  }

  private Map<String, Object> battlefieldData(final Game game, final String type) {
    return switch (type) {
      case "CONTROL" -> Map.of(CARD_ID_FIELD, permanent(game).id, "target", 1);
      case "ATTACH" -> Map.of(CARD_ID_FIELD, permanent(game).id, "targetId", permanent(game).id);
      case "POSITION" -> Map.of(CARD_ID_FIELD, permanent(game).id, "x", .25, "y", .5);
      case "COMBAT" -> Map.of(CARD_ID_FIELD, permanent(game).id, "marker", "attacker");
      case "CARD_COUNTER" ->
          Map.of(CARD_ID_FIELD, permanent(game).id, NAME_FIELD, "charge", "delta", 1);
      default -> throw new IllegalArgumentException("Unexpected battlefield fixture: " + type);
    };
  }

  private Map<String, Object> objectData(final Game game, final String type) {
    return switch (type) {
      case "TOKEN" -> Map.of(NAME_FIELD, "Zombie");
      case "COPY" -> Map.of(CARD_ID_FIELD, permanent(game).id);
      case "EDIT_TOKEN" -> {
        apply(game, "TOKEN", Map.of(NAME_FIELD, "Zombie"));
        yield Map.of(CARD_ID_FIELD, game.cards.getLast().id, NAME_FIELD, "Skeleton");
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
