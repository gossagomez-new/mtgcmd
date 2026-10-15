package dev.mtgcmd.game;

import java.util.List;
import java.util.Objects;

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

  static boolean isPrivate(final Game.Card card) {
    // Missing zone state must fail before access checks can classify the card as public.
    final String zone = Objects.requireNonNull(card.zone);
    return LIBRARY.equals(zone) || HAND.equals(zone);
  }
}
