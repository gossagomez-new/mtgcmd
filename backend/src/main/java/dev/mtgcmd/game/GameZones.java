package dev.mtgcmd.game;

import java.util.List;

/** Wire-compatible zone names shared by actions, transitions, and projections. */
final class GameZones {
  static final String LIBRARY = "library";
  static final String HAND = "hand";
  static final String BATTLEFIELD = "battlefield";
  static final String GRAVEYARD = "graveyard";
  static final String EXILE = "exile";
  static final String COMMAND = "command";
  static final String STACK = "stack";
  static final List<String> ALL =
      List.of(LIBRARY, HAND, BATTLEFIELD, GRAVEYARD, EXILE, COMMAND, STACK);

  private GameZones() {}

  static boolean isPrivate(Game.Card card) {
    return card.zone.equals(LIBRARY) || card.zone.equals(HAND);
  }
}
