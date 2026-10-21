package dev.mtgcmd.game;

import static dev.mtgcmd.game.GameLookup.*;

import dev.mtgcmd.game.Game.LibraryInspection;
import java.util.*;

/** Bounded session storage and structural validation; views never mutate saved state. */
final class LibraryInspections {
  private LibraryInspections() {}

  static LibraryInspection current(final Game game, final int actor) {
    final LibraryInspection inspection = stored(game, actor);
    if (!validMetadata(game, inspection)) {
      return null;
    }
    final List<String> current =
        zone(game, inspection.target(), "library").stream().map(card -> card.id).toList();
    return current.size() >= inspection.cardIds().size()
            && current.subList(0, inspection.cardIds().size()).equals(inspection.cardIds())
        ? inspection
        : null;
  }

  private static LibraryInspection stored(final Game game, final int actor) {
    return game.libraryInspections == null ? null : game.libraryInspections.get(actor);
  }

  private static boolean validMetadata(final Game game, final LibraryInspection inspection) {
    if (inspection == null
        || inspection.id() == null
        || inspection.id().isBlank()
        || inspection.id().length() > 80
        || inspection.target() < 0
        || inspection.target() >= game.players.size()) {
      return false;
    }
    return validSelection(inspection);
  }

  private static boolean validSelection(final LibraryInspection inspection) {
    return ("look".equals(inspection.mode()) || "scry".equals(inspection.mode()))
        && inspection.cardIds() != null
        && !inspection.cardIds().isEmpty()
        && inspection.cardIds().size() <= 100;
  }

  static void open(final Game game, final int actor, final LibraryInspection inspection) {
    if (game.libraryInspections == null) {
      game.libraryInspections = new HashMap<>();
    }
    game.libraryInspections.put(actor, inspection);
    game.searches.remove(actor);
  }

  static void close(final Game game, final int actor) {
    if (game.libraryInspections != null) {
      game.libraryInspections.remove(actor);
    }
  }

  static void invalidate(final Game game, final int owner) {
    if (game.libraryInspections != null) {
      game.libraryInspections.values().removeIf(value -> value == null || value.target() == owner);
    }
  }

  static void revoke(final Game game, final int actor, final int owner) {
    final LibraryInspection inspection = stored(game, actor);
    if (inspection != null && inspection.target() == owner) {
      close(game, actor);
    }
  }

  static void clear(final Game game) {
    if (game.libraryInspections != null) {
      game.libraryInspections.clear();
    }
  }
}
