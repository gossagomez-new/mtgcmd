package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Card;
import dev.mtgcmd.game.Game.Player;
import java.util.*;

/** Snapshot object/seat lookup with compatible validation errors. */
final class GameLookup {
  private GameLookup() {}

  static Card find(final Game game, final String identifier) {
    return game.cards.stream()
        .filter(c -> c.id.equals(identifier))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Card unavailable"));
  }

  static List<Card> zone(final Game game, final int owner, final String zone) {
    return game.cards.stream()
        .filter(c -> c.zoneOwner == owner && c.zone.equals(zone))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  static Player player(final Game game, final int seat) {
    require(seat >= 0 && seat < game.players.size(), "Unknown seat");
    return game.players.get(seat);
  }

  static void battlefield(final Card card) {
    require(
        BATTLEFIELD.equals(Objects.requireNonNull(card.zone)), "Card must be on the battlefield");
  }
}
