package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;

import dev.mtgcmd.game.Game.Player;
import java.security.SecureRandom;
import java.util.*;

/** Counter tabletop commands; revision and logging belong to the facade. */
final class CounterActions {
  private final GameAccess access;
  private final SecureRandom random;

  CounterActions(final GameAccess access, final SecureRandom random) {
    this.access = access;
    this.random = random;
  }

  String cardCounter(final Game game, final int seat, final Map<String, Object> data) {
    updateCounter(access.editable(game, seat, data).counters, data);
    return "card counter";
  }

  String counter(final Game game, final int seat, final Map<String, Object> data) {
    final Player player = player(game, seat);
    updateCounter(player.counters, data);
    return "counter";
  }

  String commanderCast(final Game game, final int seat, final Map<String, Object> data) {
    final Player player = player(game, seat);

    require(game.format().usesCommanders(), "Standard does not use commanders");
    final String key = string(data, "key", 80);
    require(
        game.cards.stream().anyMatch(c -> key.equals(c.commanderKey) && c.owner == seat),
        "Unknown commander");
    updateCounter(
        player.counters,
        Map.of("name", "casts:" + key, "delta", integer(data, "delta", 1, -100, 100)));

    return "commander cast";
  }

  String dice(Game game, int seat, final Map<String, Object> data) {
    return "rolled " + (random.nextInt(integer(data, "sides", 6, 2, 1000)) + 1);
  }

  private static void updateCounter(
      final Map<String, Integer> counters, final Map<String, Object> data) {
    final String name = string(data, "name", 100);
    require(counters.containsKey(name) || counters.size() < 100, "Too many counters");
    final int value =
        data.containsKey("value")
            ? integer(data, "value", 0, -99999, 99999)
            : counters.getOrDefault(name, 0) + integer(data, "delta", 0, -9999, 9999);
    require(value >= -99999 && value <= 99999, "Counter out of range");
    counters.put(name, value);
  }
}
