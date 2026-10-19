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
  private static final String ACTIVE_ERROR = "Only the active player can advance turns";
  private final CardZoneTransitions transitions;

  LifecycleActions(final CardZoneTransitions transitions) {
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

  String ready(final Game game, final int seat, final Map<String, Object> data) {
    final Player player = player(game, seat);

    require("lobby".equals(Objects.requireNonNull(game.status)), "Game has already started");
    final boolean ready = bool(data, "ready", true), accepted = bool(data, "acceptWarnings", false);
    require(!ready || !player.deck.isEmpty(), "Import a deck first");
    require(
        !ready || player.warnings.isEmpty() || accepted,
        "Accept deck warnings before becoming ready");
    player.ready = ready;
    player.acceptedWarnings = accepted;

    return "ready";
  }

  String start(final Game game, final int seat, final Map<String, Object> data) {

    require(seat == game.host, "Only the host can start");
    require("lobby".equals(Objects.requireNonNull(game.status)), "Game has already started");
    require(
        game.players.size() == game.format().seatCapacity()
            && game.players.stream().allMatch(x -> x.ready),
        game.format() == GameFormat.COMMANDER
            ? "Four ready players are required"
            : "Two ready players are required");
    if (game.format() == GameFormat.STANDARD) {
      game.active = integer(data, "firstPlayer", game.host, 0, game.players.size() - 1);
    }
    start(game);

    return "start";
  }

  String phase(final Game game, final int seat, Map<String, Object> data) {

    require(seat == game.active, ACTIVE_ERROR);
    final int index = PHASES.indexOf(game.phase);
    if (index == PHASES.size() - 1) {
      advanceTurn(game);
    } else {
      game.phase = PHASES.get(index + 1);
    }
    return "advanced to " + game.phase;
  }

  String endTurn(final Game game, final int seat, Map<String, Object> data) {
    require(seat == game.active, ACTIVE_ERROR);
    advanceTurn(game);
    return "ended the turn";
  }

  private static void advanceTurn(final Game game) {
    for (int index = 0; index < game.players.size(); index++) {
      game.active = (game.active + 1) % game.players.size();
      if (!game.players.get(game.active).conceded) {
        break;
      }
    }
    game.turn++;
    game.phase = PHASES.getFirst();
    game.cards.forEach(card -> card.combat = "");
  }

  String concede(final Game game, final int seat, Map<String, Object> data) {
    final Player player = player(game, seat);

    player.conceded = true;
    game.grants.remove(seat);
    game.grants.values().forEach(s -> s.remove(seat));
    game.searches.remove(seat);
    if (game.active == seat && game.players.stream().anyMatch(other -> !other.conceded)) {
      do {
        game.active = (game.active + 1) % game.players.size();
      } while (game.players.get(game.active).conceded);
      game.turn++;
      game.phase = PHASES.getFirst();
      game.cards.forEach(c -> c.combat = "");
    }

    return "concede";
  }

  String finish(final Game game, final int seat, Map<String, Object> data) {

    require(seat == game.host, "Only the host can finish");
    game.status = "finished";

    return "finish";
  }

  private void start(final Game game) {
    for (final Player p : game.players) {
      final Set<String> remaining = new HashSet<>(p.commanders);
      for (final Definition def : p.deck) {
        final Card card = new Card();
        card.definition = def;
        card.owner = p.seat;
        card.controller = p.seat;
        card.zoneOwner = p.seat;
        final boolean commander = remaining.remove(def.id());
        card.zone = commander ? COMMAND : LIBRARY;
        card.commanderKey = commander ? UUID.randomUUID().toString() : null;
        game.cards.add(card);
      }
      transitions.shuffle(game, p.seat);
      for (final Card c : zone(game, p.seat, LIBRARY).stream().limit(7).toList()) {
        transitions.move(game, c, HAND, false, false);
      }
    }
    game.status = "playing";
  }
}
