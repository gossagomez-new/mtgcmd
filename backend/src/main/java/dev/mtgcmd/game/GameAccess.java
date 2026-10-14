package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Card;
import java.util.*;

/** Shared policies for object visibility, definition inspection, and editing. */
final class GameAccess {
  boolean visible(Game g, int seat, Card c) {
    if (!isPrivate(c)) return true;
    if (c.revealedTo.contains(seat)) return true;
    if (!canAccessPrivate(g, seat, c.zoneOwner)) return false;
    return !c.zone.equals(LIBRARY) || Objects.equals(g.searches.get(seat), c.zoneOwner);
  }

  boolean canSeeDefinition(Game g, int seat, Card c) {
    return visible(g, seat, c)
        && (!c.faceDown || c.controller == seat || c.revealedTo.contains(seat));
  }

  boolean mayEdit(Game g, int seat, Card c) {
    if (player(g, seat).conceded) return false;
    if (isPrivate(c))
      return canAccessPrivate(g, seat, c.zoneOwner)
          && (!c.zone.equals(LIBRARY) || Objects.equals(g.searches.get(seat), c.zoneOwner));
    return c.controller == seat;
  }

  Card editable(Game g, int seat, Map<String, Object> d) {
    Card c = find(g, string(d, "cardId", 80));
    if (c.zone.equals(LIBRARY) && canAccessPrivate(g, seat, c.zoneOwner))
      require(Objects.equals(g.searches.get(seat), c.zoneOwner), "Open a library search first");
    require(mayEdit(g, seat, c), "No permission to change this card");
    return c;
  }

  boolean canAccessPrivate(Game g, int seat, int owner) {
    return seat == owner || g.grants.getOrDefault(owner, Set.of()).contains(seat);
  }

  void privatePermission(Game g, int seat, int owner) {
    player(g, owner);
    require(canAccessPrivate(g, seat, owner), "No permission for this zone");
  }
}
