package dev.mtgcmd.game;

import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import java.util.*;

/** Seat-filtered projection of the existing snapshot wire shape. */
final class GameViewMapper {
  private final GameAccess access;

  GameViewMapper(final GameAccess access) {
    this.access = access;
  }

  Map<String, Object> forPlayer(final Game game, final int seat) {
    player(game, seat);
    final Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", game.id);
    out.put("revision", game.revision);
    out.put("status", game.status);
    out.put("format", game.format().name());
    out.put("seatCapacity", game.format().seatCapacity());
    out.put("host", game.host);
    out.put("active", game.active);
    out.put("turn", game.turn);
    out.put("turnVisit", TurnHistory.visit(game));
    out.put(
        "canGiveBackTurn",
        "playing".equals(game.status)
            && seat == game.active
            && !game.players.get(seat).conceded
            && TurnHistory.available(game));
    out.put("phase", game.phase);
    out.put("you", seat);
    out.put("grants", game.grants.getOrDefault(seat, Set.of()));
    out.put("search", game.searches.get(seat));
    out.put("log", game.log);
    out.put(
        "players",
        game.players.stream()
            .map(
                p -> {
                  final Map<String, Object> view = new LinkedHashMap<>();
                  view.put("seat", p.seat);
                  view.put("name", p.name);
                  view.put("ready", p.ready);
                  view.put("conceded", p.conceded);
                  view.put("deckCount", p.deck.size());
                  view.put("deckRevision", p.deckRevision);
                  view.put("privateAccess", access.canAccessPrivate(game, seat, p.seat));
                  view.put("warnings", p.seat == seat ? p.warnings : List.of());
                  view.put("counters", p.counters);
                  final Map<String, Long> counts = new LinkedHashMap<>();
                  for (final String z : ALL) {
                    counts.put(z, (long) zone(game, p.seat, z).size());
                  }
                  view.put("counts", counts);
                  view.put(
                      "commanders",
                      game.cards.stream()
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
                  return view;
                })
            .toList());
    out.put(
        "cards",
        game.cards.stream()
            .filter(c -> access.visible(game, seat, c))
            .map(
                c -> {
                  final Map<String, Object> view = new LinkedHashMap<>();
                  view.put("id", c.id);
                  view.put("owner", c.owner);
                  view.put("controller", c.controller);
                  view.put("zoneOwner", c.zoneOwner);
                  view.put("zone", c.zone);
                  view.put("tapped", c.tapped);
                  view.put("faceDown", c.faceDown);
                  view.put("face", c.face);
                  view.put("token", c.token);
                  view.put(
                      "commanderKey",
                      access.canSeeDefinition(game, seat, c) ? c.commanderKey : null);
                  view.put("attachedTo", c.attachedTo);
                  view.put("combat", c.combat);
                  view.put("stackOrder", c.stackOrder);
                  view.put("x", c.x);
                  view.put("y", c.y);
                  view.put("counters", c.counters);
                  view.put("editable", access.mayEdit(game, seat, c));
                  if (access.canSeeDefinition(game, seat, c)) {
                    view.put("definition", c.definition);
                  }
                  return view;
                })
            .toList());
    out.put("abilities", game.abilities);
    return out;
  }
}
