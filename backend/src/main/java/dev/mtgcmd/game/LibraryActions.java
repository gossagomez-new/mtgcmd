package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.Game.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import java.util.*;

/** Library tabletop commands; revision and logging belong to the facade. */
final class LibraryActions {
  private final GameAccess access;
  private final CardZoneTransitions transitions;

  LibraryActions(GameAccess access, CardZoneTransitions transitions) {
    this.access = access;
    this.transitions = transitions;
  }

  String draw(Game g, int seat, Map<String, Object> d) {

    int target = integer(d, "target", seat, 0, g.players.size() - 1),
        count = integer(d, "count", 1, 1, 100);
    access.privatePermission(g, seat, target);
    List<Card> library = zone(g, target, LIBRARY);
    require(library.size() >= count, "Not enough cards in library");
    for (Card c : library.subList(0, count)) transitions.move(g, c, HAND, false, false);
    return "drew " + count + " card(s)";
  }

  String shuffle(Game g, int seat, Map<String, Object> d) {

    int target = integer(d, "target", seat, 0, g.players.size() - 1);
    access.privatePermission(g, seat, target);
    transitions.shuffle(g, target);
    return "shuffled a library";
  }

  String search(Game g, int seat, Map<String, Object> d) {

    int target = integer(d, "target", seat, 0, g.players.size() - 1);
    access.privatePermission(g, seat, target);
    g.searches.put(seat, target);
    return "opened a private library search";
  }

  String closeSearch(Game g, int seat, Map<String, Object> d) {
    g.searches.remove(seat);
    return "close search";
  }

  String grant(Game g, int seat, Map<String, Object> d) {

    int target = integer(d, "target", -1, 0, g.players.size() - 1);
    player(g, target);
    Set<Integer> grants = g.grants.computeIfAbsent(seat, x -> new HashSet<>());
    if (bool(d, "allow", true)) grants.add(target);
    else {
      grants.remove(target);
      if (Objects.equals(g.searches.get(target), seat)) g.searches.remove(target);
    }

    return "grant";
  }
}
