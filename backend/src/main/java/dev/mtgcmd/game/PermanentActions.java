package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.Game.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import java.util.*;

/** Permanent tabletop commands; revision and logging belong to the facade. */
final class PermanentActions {
  private final GameAccess access;
  private final CardZoneTransitions transitions;

  PermanentActions(GameAccess access, CardZoneTransitions transitions) {
    this.access = access;
    this.transitions = transitions;
  }

  String move(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    String destination = string(d, "zone", 20);
    require(ALL.contains(destination), "Unknown zone");
    if (destination.equals(COMMAND))
      require(c.commanderKey != null, "Only designated commanders enter the command zone");
    transitions.move(g, c, destination, bool(d, "top", false), bool(d, "faceDown", false));
    c.stackOrder = g.revision + 1;

    return "move";
  }

  String tap(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    battlefield(c);
    c.tapped = bool(d, "tapped", !c.tapped);

    return "tap";
  }

  String untapAll(Game g, int seat, Map<String, Object> d) {
    g.cards.stream()
        .filter(c -> c.zone.equals(BATTLEFIELD) && c.controller == seat)
        .forEach(c -> c.tapped = false);
    return "untap all";
  }

  String faceDown(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    require(!isPrivate(c), "Face-down applies to public objects");
    c.faceDown = bool(d, "faceDown", !c.faceDown);
    c.revealedTo.clear();

    return "face down";
  }

  String transform(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    require(c.definition.faces().size() > 1, "Card has no other face");
    c.face = (c.face + 1) % c.definition.faces().size();

    return "transform";
  }

  String reveal(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    Object audience = d.get("audience");
    require(audience instanceof List<?>, "Reveal requires an audience");
    Set<Integer> seats = new HashSet<>();
    for (Object value : (List<?>) audience) {
      require(value instanceof Number, "Invalid audience");
      int viewer = exactInteger((Number) value, "audience", 0, g.players.size() - 1);
      player(g, viewer);
      seats.add(viewer);
    }
    c.revealedTo = seats;

    return "reveal";
  }

  String control(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    battlefield(c);
    int target = integer(d, "target", -1, 0, g.players.size() - 1);
    require(!player(g, target).conceded, "Target player conceded");
    c.controller = target;
    c.zoneOwner = target;
    c.combat = "";

    return "control";
  }

  String attach(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    battlefield(c);
    String targetId = optionalString(d, "targetId", 80);
    if (targetId.isEmpty()) c.attachedTo = null;
    else {
      Card target = find(g, targetId);
      battlefield(target);
      Set<String> visited = new HashSet<>(Set.of(c.id));
      Card cursor = target;
      while (cursor != null) {
        require(visited.add(cursor.id), "Attachment cycle");
        cursor = cursor.attachedTo == null ? null : find(g, cursor.attachedTo);
      }
      c.attachedTo = target.id;
    }

    return "attach";
  }

  String position(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    battlefield(c);
    double x = decimal(d, "x"), y = decimal(d, "y");
    c.x = x;
    c.y = y;

    return "position";
  }

  String combat(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    battlefield(c);
    String marker = optionalString(d, "marker", 80);
    require(
        marker.isEmpty() || marker.equals("attacker") || marker.startsWith("blocks:"),
        "Invalid combat marker");
    if (marker.startsWith("blocks:")) battlefield(find(g, marker.substring(7)));
    c.combat = marker;

    return "combat";
  }
}
