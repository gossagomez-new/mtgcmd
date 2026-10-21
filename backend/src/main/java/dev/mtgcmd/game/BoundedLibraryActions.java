package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;

import dev.mtgcmd.game.Game.Card;
import dev.mtgcmd.game.Game.LibraryInspection;
import java.util.*;

/** Server-selected inspection and exact-partition scry; RoomStore owns atomic persistence. */
final class BoundedLibraryActions {
  private final GameAccess access;
  private final CardZoneTransitions transitions;

  BoundedLibraryActions(final GameAccess access, final CardZoneTransitions transitions) {
    this.access = access;
    this.transitions = transitions;
  }

  String look(final Game game, final int actor, final Map<String, Object> data) {
    return open(game, actor, data, "look");
  }

  String scry(final Game game, final int actor, final Map<String, Object> data) {
    return open(game, actor, data, "scry");
  }

  private String open(
      final Game game, final int actor, final Map<String, Object> data, final String mode) {
    final int target = target(game, actor, data);
    final List<Card> top = top(game, target, data);
    LibraryInspections.open(
        game,
        actor,
        new LibraryInspection(
            UUID.randomUUID().toString(),
            target,
            mode,
            top.stream().map(card -> card.id).toList()));
    return "opened a private "
        + mode
        + " of "
        + top.size()
        + " card(s) from "
        + player(game, target).name
        + "'s library";
  }

  String exile(final Game game, final int actor, final Map<String, Object> data) {
    final int target = target(game, actor, data);
    final List<Card> top = top(game, target, data);
    for (final Card card : top) {
      transitions.move(game, card, "exile", false, false);
    }
    return "exiled " + top.size() + " card(s) from " + player(game, target).name + "'s library";
  }

  String close(final Game game, final int actor, final Map<String, Object> data) {
    session(game, actor, data);
    LibraryInspections.close(game, actor);
    return "closed a private library inspection";
  }

  String applyScry(final Game game, final int actor, final Map<String, Object> data) {
    final LibraryInspection inspection = session(game, actor, data);
    require("scry".equals(inspection.mode()), "Inspection is not a scry");
    final List<String> top = identifiers(data, "top");
    final List<String> bottom = identifiers(data, "bottom");
    final List<String> partition = new ArrayList<>(top);
    partition.addAll(bottom);
    require(
        partition.size() == inspection.cardIds().size()
            && new HashSet<>(partition).size() == partition.size()
            && new HashSet<>(partition).equals(new HashSet<>(inspection.cardIds())),
        "Invalid scry partition");
    final List<Card> library = zone(game, inspection.target(), "library");
    final Map<String, Card> cards = new HashMap<>();
    library.forEach(card -> cards.put(card.id, card));
    final List<Card> ordered = new ArrayList<>();
    top.forEach(identifier -> ordered.add(cards.get(identifier)));
    ordered.addAll(library.subList(inspection.cardIds().size(), library.size()));
    bottom.forEach(identifier -> ordered.add(cards.get(identifier)));
    transitions.reorderLibrary(game, inspection.target(), ordered);
    return "scried "
        + partition.size()
        + " card(s) from "
        + player(game, inspection.target()).name
        + "'s library";
  }

  private LibraryInspection session(
      final Game game, final int actor, final Map<String, Object> data) {
    final String inspectionId = string(data, "inspectionId", 80);
    final LibraryInspection inspection = access.libraryInspection(game, actor);
    require(
        inspection != null && inspection.id().equals(inspectionId), "No active library inspection");
    return inspection;
  }

  private int target(final Game game, final int actor, final Map<String, Object> data) {
    final int target = integer(data, "target", actor, 0, game.players.size() - 1);
    access.privatePermission(game, actor, target);
    require(!player(game, target).conceded, "Target has conceded");
    return target;
  }

  private static List<Card> top(final Game game, final int target, final Map<String, Object> data) {
    final int count = integer(data, "count", 1, 1, 100);
    final List<Card> library = zone(game, target, "library");
    require(!library.isEmpty(), "Library is empty");
    return new ArrayList<>(library.subList(0, Math.min(count, library.size())));
  }

  private static List<String> identifiers(final Map<String, Object> data, final String key) {
    require(data.get(key) instanceof List<?> values && values.size() <= 100, "Invalid " + key);
    final List<String> ids = new ArrayList<>();
    for (final Object value : (List<?>) data.get(key)) {
      require(value instanceof String, "Invalid " + key);
      final String identifier = (String) value;
      require(!identifier.isBlank() && identifier.length() <= 80, "Invalid " + key);
      ids.add(identifier);
    }
    return ids;
  }
}
