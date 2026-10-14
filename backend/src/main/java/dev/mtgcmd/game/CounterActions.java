package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.Game.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import java.security.SecureRandom;
import java.util.*;

/** Counter tabletop commands; revision and logging belong to the facade. */
final class CounterActions {
  private final GameAccess access;
  private final SecureRandom random;

  CounterActions(GameAccess access, SecureRandom random) {
    this.access = access;
    this.random = random;
  }

  String cardCounter(Game g, int seat, Map<String, Object> d) {
    updateCounter(access.editable(g, seat, d).counters, d);
    return "card counter";
  }

  String counter(Game g, int seat, Map<String, Object> d) {
    Player p = player(g, seat);
    updateCounter(p.counters, d);
    return "counter";
  }

  String commanderCast(Game g, int seat, Map<String, Object> d) {
    Player p = player(g, seat);

    require(g.format().usesCommanders(), "Standard does not use commanders");
    String key = string(d, "key", 80);
    require(
        g.cards.stream().anyMatch(c -> key.equals(c.commanderKey) && c.owner == seat),
        "Unknown commander");
    updateCounter(
        p.counters, Map.of("name", "casts:" + key, "delta", integer(d, "delta", 1, -100, 100)));

    return "commander cast";
  }

  String dice(Game g, int seat, Map<String, Object> d) {
    return "rolled " + (random.nextInt(integer(d, "sides", 6, 2, 1000)) + 1);
  }

  private static void updateCounter(Map<String, Integer> counters, Map<String, Object> d) {
    String name = string(d, "name", 100);
    require(counters.containsKey(name) || counters.size() < 100, "Too many counters");
    int value =
        d.containsKey("value")
            ? integer(d, "value", 0, -99999, 99999)
            : counters.getOrDefault(name, 0) + integer(d, "delta", 0, -9999, 9999);
    require(value >= -99999 && value <= 99999, "Counter out of range");
    counters.put(name, value);
  }
}
