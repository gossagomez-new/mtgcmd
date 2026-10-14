package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.Game.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import java.util.*;

/** Snapshot object/seat lookup with compatible validation errors. */
final class GameLookup {
  private GameLookup() {}

  static Card find(Game g, String id) {
    return g.cards.stream()
        .filter(c -> c.id.equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Card unavailable"));
  }

  static List<Card> zone(Game g, int owner, String zone) {
    return g.cards.stream()
        .filter(c -> c.zoneOwner == owner && c.zone.equals(zone))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  static Player player(Game g, int seat) {
    require(seat >= 0 && seat < g.players.size(), "Unknown seat");
    return g.players.get(seat);
  }

  static void battlefield(Card c) {
    require(c.zone.equals(BATTLEFIELD), "Card must be on the battlefield");
  }
}
