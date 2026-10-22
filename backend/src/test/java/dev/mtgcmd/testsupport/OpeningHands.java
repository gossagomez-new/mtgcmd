package dev.mtgcmd.testsupport;

import dev.mtgcmd.game.Game;
import dev.mtgcmd.game.GameEngine;
import java.util.*;

/** Explicitly finish opening selection in fixtures whose subject is normal gameplay. */
public final class OpeningHands {
  private OpeningHands() {}

  public static void keepAll(final GameEngine engine, final Game game) {
    for (final Game.Player player : game.players) {
      engine.apply(
          game,
          player.seat,
          new Game.Command(
              UUID.randomUUID().toString(),
              game.revision,
              "KEEP_HAND",
              Map.of("bottom", List.of())));
    }
  }
}
