package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Card;
import java.util.*;

/** Permanent tabletop commands; revision and logging belong to the facade. */
final class PermanentActions {
  private final GameAccess access;
  private final CardZoneTransitions transitions;

  PermanentActions(final GameAccess access, final CardZoneTransitions transitions) {
    this.access = access;
    this.transitions = transitions;
  }

  String move(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    final String destination = string(data, "zone", 20);
    require(ALL.contains(destination), "Unknown zone");
    if (COMMAND.equals(Objects.requireNonNull(destination))) {
      require(card.commanderKey != null, "Only designated commanders enter the command zone");
    }
    transitions.move(
        game, card, destination, bool(data, "top", false), bool(data, "faceDown", false));
    card.stackOrder = game.revision + 1;

    return "move";
  }

  String tap(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    battlefield(card);
    card.tapped = bool(data, "tapped", !card.tapped);

    return "tap";
  }

  String tapSelected(final Game game, final int seat, final Map<String, Object> data) {
    final Object input = data.get("cardIds");
    require(input instanceof List<?>, "Invalid card selection");
    final List<?> identifiers = (List<?>) input;
    require(!identifiers.isEmpty() && identifiers.size() <= 500, "Invalid card selection");
    final Set<String> unique = new HashSet<>();
    for (final Object identifier : identifiers) {
      require(identifier instanceof String, "Invalid card selection");
      final String id = (String) identifier;
      require(
          !id.isBlank() && id.length() <= 80 && unique.add(id.trim()), "Invalid card selection");
    }
    require(data.get("tapped") instanceof Boolean, "tapped must be true or false");
    final boolean tapped = (Boolean) data.get("tapped");
    final List<Card> selected = new ArrayList<>();
    for (final Object identifier : identifiers) {
      final Card card = access.editable(game, seat, Map.of("cardId", identifier));
      battlefield(card);
      selected.add(card);
    }
    selected.forEach(card -> card.tapped = tapped);
    return tapped ? "tap selected" : "untap selected";
  }

  String untapAll(final Game game, final int seat, Map<String, Object> data) {
    game.cards.stream()
        .filter(c -> BATTLEFIELD.equals(Objects.requireNonNull(c.zone)) && c.controller == seat)
        .forEach(c -> c.tapped = false);
    return "untap all";
  }

  String faceDown(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    require(!isPrivate(card), "Face-down applies to public objects");
    card.faceDown = bool(data, "faceDown", !card.faceDown);
    card.revealedTo.clear();

    return "face down";
  }

  String transform(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    require(card.definition.faces().size() > 1, "Card has no other face");
    card.face = (card.face + 1) % card.definition.faces().size();

    return "transform";
  }

  String reveal(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    final Object audience = data.get("audience");
    require(audience instanceof List<?>, "Reveal requires an audience");
    final Set<Integer> seats = new HashSet<>();
    for (final Object value : (List<?>) audience) {
      require(value instanceof Number, "Invalid audience");
      final int viewer = exactInteger((Number) value, "audience", 0, game.players.size() - 1);
      player(game, viewer);
      seats.add(viewer);
    }
    card.revealedTo = seats;

    return "reveal";
  }

  String control(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    battlefield(card);
    final int target = integer(data, "target", -1, 0, game.players.size() - 1);
    require(!player(game, target).conceded, "Target player conceded");
    card.controller = target;
    card.zoneOwner = target;
    card.combat = "";

    return "control";
  }

  String attach(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    battlefield(card);
    final String targetId = optionalString(data, "targetId", 80);
    if (targetId.isEmpty()) {
      card.attachedTo = null;
    } else {
      final Card target = find(game, targetId);
      battlefield(target);
      final Set<String> visited = new HashSet<>(Set.of(card.id));
      Card cursor = target;
      while (cursor != null) {
        require(visited.add(cursor.id), "Attachment cycle");
        cursor = cursor.attachedTo == null ? null : find(game, cursor.attachedTo);
      }
      card.attachedTo = target.id;
    }

    return "attach";
  }

  String position(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    battlefield(card);
    final double horizontal = decimal(data, "x"), vertical = decimal(data, "y");
    card.x = horizontal;
    card.y = vertical;

    return "position";
  }

  String combat(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    battlefield(card);
    final String marker = optionalString(data, "marker", 80);
    require(
        marker.isEmpty()
            || "attacker".equals(Objects.requireNonNull(marker))
            || marker.startsWith("blocks:"),
        "Invalid combat marker");
    if (marker.startsWith("blocks:")) {
      battlefield(find(game, marker.substring(7)));
    }
    card.combat = marker;

    return "combat";
  }
}
