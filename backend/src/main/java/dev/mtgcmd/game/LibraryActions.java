package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Card;
import java.util.*;

/** Library tabletop commands; revision and logging belong to the facade. */
final class LibraryActions {
  private static final String TARGET_FIELD = "target";

  private final GameAccess access;
  private final CardZoneTransitions transitions;

  LibraryActions(final GameAccess access, final CardZoneTransitions transitions) {
    this.access = access;
    this.transitions = transitions;
  }

  String draw(final Game game, final int seat, final Map<String, Object> data) {

    final int target = integer(data, TARGET_FIELD, seat, 0, game.players.size() - 1),
        count = integer(data, "count", 1, 1, 100);
    access.privatePermission(game, seat, target);
    final List<Card> library = zone(game, target, LIBRARY);
    require(library.size() >= count, "Not enough cards in library");
    for (final Card c : library.subList(0, count)) {
      transitions.move(game, c, HAND, false, false);
    }
    return "drew " + count + " card(s)";
  }

  String shuffle(final Game game, final int seat, final Map<String, Object> data) {

    final int target = integer(data, TARGET_FIELD, seat, 0, game.players.size() - 1);
    access.privatePermission(game, seat, target);
    transitions.shuffle(game, target);
    return "shuffled a library";
  }

  String search(final Game game, final int seat, final Map<String, Object> data) {

    final int target = integer(data, TARGET_FIELD, seat, 0, game.players.size() - 1);
    access.privatePermission(game, seat, target);
    game.searches.put(seat, target);
    return "opened a private library search";
  }

  String closeSearch(final Game game, final int seat, Map<String, Object> data) {
    game.searches.remove(seat);
    return "close search";
  }

  String grant(final Game game, final int seat, final Map<String, Object> data) {

    final int target = integer(data, TARGET_FIELD, -1, 0, game.players.size() - 1);
    player(game, target);
    final Set<Integer> grants = game.grants.computeIfAbsent(seat, x -> new HashSet<>());
    if (bool(data, "allow", true)) {
      grants.add(target);
    } else {
      grants.remove(target);
      if (Objects.equals(game.searches.get(target), seat)) {
        game.searches.remove(target);
      }
    }

    return "grant";
  }
}
