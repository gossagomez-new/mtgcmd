package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.require;

import java.util.List;

/** Bounded turn checkpoints; returning never restores unrelated gameplay state. */
final class TurnHistory {
  private static final int LIMIT = 100;
  private static final long MAX_VISIT = 9_007_199_254_740_991L;

  private TurnHistory() {}

  static long visit(final Game game) {
    return game.turnTracking == null ? 0 : game.turnTracking.visit;
  }

  static boolean available(final Game game) {
    final Game.TurnCheckpoint previous = previous(game);
    return hasVisitCapacity(game)
        && valid(game, previous)
        && !game.players.get(previous.active()).conceded;
  }

  static void record(final Game game) {
    final long nextVisit = nextVisit(game);
    if (game.turnTracking == null) {
      game.turnTracking = new Game.TurnTracking();
    }
    if (game.turnTracking.history == null) {
      game.turnTracking.history = new java.util.ArrayList<>();
    }
    final List<Game.TurnCheckpoint> history = game.turnTracking.history;
    while (history.size() >= LIMIT) {
      history.removeFirst();
    }
    history.add(new Game.TurnCheckpoint(game.active, game.turn, game.phase));
    game.turnTracking.visit = nextVisit;
  }

  static String giveBack(final Game game) {
    final Game.TurnCheckpoint previous = previous(game);
    require(previous != null, "No previous turn is available");
    require(valid(game, previous), "Previous turn is invalid");
    require(!game.players.get(previous.active()).conceded, "Previous player has conceded");
    final long nextVisit = nextVisit(game);
    game.turnTracking.history.removeLast();
    game.turnTracking.visit = nextVisit;
    game.active = previous.active();
    game.turn = previous.turn();
    game.phase = previous.phase();
    return "gave the turn back to "
        + game.players.get(game.active).name
        + " (turn "
        + game.turn
        + ", "
        + game.phase
        + ")";
  }

  private static long nextVisit(final Game game) {
    require(hasVisitCapacity(game), "Turn sequence exhausted");
    return visit(game) + 1;
  }

  private static boolean hasVisitCapacity(final Game game) {
    final long current = visit(game);
    return current >= 0 && current < MAX_VISIT;
  }

  private static Game.TurnCheckpoint previous(final Game game) {
    if (game.turnTracking == null
        || game.turnTracking.history == null
        || game.turnTracking.history.isEmpty()) {
      return null;
    }
    return game.turnTracking.history.getLast();
  }

  private static boolean valid(final Game game, final Game.TurnCheckpoint previous) {
    return previous != null
        && previous.active() >= 0
        && previous.active() < game.players.size()
        && previous.turn() > 0
        && previous.turn() < game.turn
        && previous.phase() != null
        && LifecycleActions.PHASES.contains(previous.phase());
  }
}
