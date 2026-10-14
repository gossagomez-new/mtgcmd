package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.Game.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import java.util.*;

/** Seat-filtered projection of the existing snapshot wire shape. */
final class GameViewMapper {
  private final GameAccess access;

  GameViewMapper(GameAccess access) {
    this.access = access;
  }

  Map<String, Object> forPlayer(Game g, int seat) {
    player(g, seat);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", g.id);
    out.put("revision", g.revision);
    out.put("status", g.status);
    out.put("format", g.format().name());
    out.put("seatCapacity", g.format().seatCapacity());
    out.put("host", g.host);
    out.put("active", g.active);
    out.put("turn", g.turn);
    out.put("phase", g.phase);
    out.put("you", seat);
    out.put("grants", g.grants.getOrDefault(seat, Set.of()));
    out.put("search", g.searches.get(seat));
    out.put("log", g.log);
    out.put(
        "players",
        g.players.stream()
            .map(
                p -> {
                  Map<String, Object> v = new LinkedHashMap<>();
                  v.put("seat", p.seat);
                  v.put("name", p.name);
                  v.put("ready", p.ready);
                  v.put("conceded", p.conceded);
                  v.put("deckCount", p.deck.size());
                  v.put("deckRevision", p.deckRevision);
                  v.put("privateAccess", access.canAccessPrivate(g, seat, p.seat));
                  v.put("warnings", p.seat == seat ? p.warnings : List.of());
                  v.put("counters", p.counters);
                  Map<String, Long> counts = new LinkedHashMap<>();
                  for (String z : ALL) counts.put(z, (long) zone(g, p.seat, z).size());
                  v.put("counts", counts);
                  v.put(
                      "commanders",
                      g.cards.stream()
                          .filter(c -> c.owner == p.seat && c.commanderKey != null)
                          .map(
                              c ->
                                  Map.of(
                                      "key",
                                      c.commanderKey,
                                      "name",
                                      c.definition.name(),
                                      "casts",
                                      p.counters.getOrDefault("casts:" + c.commanderKey, 0)))
                          .toList());
                  return v;
                })
            .toList());
    out.put(
        "cards",
        g.cards.stream()
            .filter(c -> access.visible(g, seat, c))
            .map(
                c -> {
                  Map<String, Object> v = new LinkedHashMap<>();
                  v.put("id", c.id);
                  v.put("owner", c.owner);
                  v.put("controller", c.controller);
                  v.put("zoneOwner", c.zoneOwner);
                  v.put("zone", c.zone);
                  v.put("tapped", c.tapped);
                  v.put("faceDown", c.faceDown);
                  v.put("face", c.face);
                  v.put("token", c.token);
                  v.put(
                      "commanderKey", access.canSeeDefinition(g, seat, c) ? c.commanderKey : null);
                  v.put("attachedTo", c.attachedTo);
                  v.put("combat", c.combat);
                  v.put("stackOrder", c.stackOrder);
                  v.put("x", c.x);
                  v.put("y", c.y);
                  v.put("counters", c.counters);
                  v.put("editable", access.mayEdit(g, seat, c));
                  if (access.canSeeDefinition(g, seat, c)) v.put("definition", c.definition);
                  return v;
                })
            .toList());
    out.put("abilities", g.abilities);
    return out;
  }
}
