package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Card;
import dev.mtgcmd.game.Game.Definition;
import dev.mtgcmd.game.Game.Player;
import java.util.*;

/** Lifecycle tabletop commands; revision and logging belong to the facade. */
final class LifecycleActions {
  private final CardZoneTransitions transitions;

  LifecycleActions(CardZoneTransitions transitions) {
    this.transitions = transitions;
  }

  static final List<String> PHASES =
      List.of(
          "Untap",
          "Upkeep",
          "Draw",
          "Main 1",
          "Begin combat",
          "Attackers",
          "Blockers",
          "Damage",
          "End combat",
          "Main 2",
          "End step",
          "Cleanup");

  String ready(Game g, int seat, Map<String, Object> d) {
    Player p = player(g, seat);

    require(g.status.equals("lobby"), "Game has already started");
    boolean ready = bool(d, "ready", true), accepted = bool(d, "acceptWarnings", false);
    require(!ready || !p.deck.isEmpty(), "Import a deck first");
    require(
        !ready || p.warnings.isEmpty() || accepted, "Accept deck warnings before becoming ready");
    p.ready = ready;
    p.acceptedWarnings = accepted;

    return "ready";
  }

  String start(Game g, int seat, Map<String, Object> d) {

    require(seat == g.host, "Only the host can start");
    require(g.status.equals("lobby"), "Game has already started");
    require(
        g.players.size() == g.format().seatCapacity() && g.players.stream().allMatch(x -> x.ready),
        g.format() == GameFormat.COMMANDER
            ? "Four ready players are required"
            : "Two ready players are required");
    if (g.format() == GameFormat.STANDARD)
      g.active = integer(d, "firstPlayer", g.host, 0, g.players.size() - 1);
    start(g);

    return "start";
  }

  String phase(Game g, int seat, Map<String, Object> d) {

    require(seat == g.active || seat == g.host, "Only the active player or host advances phases");
    int index = PHASES.indexOf(g.phase);
    if (index == PHASES.size() - 1) {
      for (int i = 0; i < g.players.size(); i++) {
        g.active = (g.active + 1) % g.players.size();
        if (!g.players.get(g.active).conceded) break;
      }
      g.turn++;
      g.phase = PHASES.getFirst();
      g.cards.forEach(c -> c.combat = "");
    } else g.phase = PHASES.get(index + 1);
    return "advanced to " + g.phase;
  }

  String concede(Game g, int seat, Map<String, Object> d) {
    Player p = player(g, seat);

    p.conceded = true;
    g.grants.remove(seat);
    g.grants.values().forEach(s -> s.remove(seat));
    g.searches.remove(seat);
    if (g.active == seat && g.players.stream().anyMatch(other -> !other.conceded)) {
      do {
        g.active = (g.active + 1) % g.players.size();
      } while (g.players.get(g.active).conceded);
      g.turn++;
      g.phase = PHASES.getFirst();
      g.cards.forEach(c -> c.combat = "");
    }

    return "concede";
  }

  String finish(Game g, int seat, Map<String, Object> d) {

    require(seat == g.host, "Only the host can finish");
    g.status = "finished";

    return "finish";
  }

  private void start(Game g) {
    for (Player p : g.players) {
      Set<String> remaining = new HashSet<>(p.commanders);
      for (Definition def : p.deck) {
        Card c = new Card();
        c.definition = def;
        c.owner = p.seat;
        c.controller = p.seat;
        c.zoneOwner = p.seat;
        boolean commander = remaining.remove(def.id());
        c.zone = commander ? COMMAND : LIBRARY;
        c.commanderKey = commander ? UUID.randomUUID().toString() : null;
        g.cards.add(c);
      }
      transitions.shuffle(g, p.seat);
      for (Card c : zone(g, p.seat, LIBRARY).stream().limit(7).toList())
        transitions.move(g, c, HAND, false, false);
    }
    g.status = "playing";
  }
}
