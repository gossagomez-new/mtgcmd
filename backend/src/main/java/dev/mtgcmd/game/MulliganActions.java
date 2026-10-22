package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import java.util.*;

/** Opening-hand policy; transitions own card movement, RoomStore owns atomic persistence. */
final class MulliganActions {
  private static final String LONDON = "LONDON";
  private static final String UNLIMITED = "UNLIMITED";
  private final CardZoneTransitions transitions;

  MulliganActions(final CardZoneTransitions transitions) {
    this.transitions = transitions;
  }

  static String mode(final Game game) {
    return game.mulligans == null || game.mulligans.mode == null ? LONDON : game.mulligans.mode;
  }

  static int free(final Game game) {
    return game.mulligans == null || game.mulligans.freeMulligans == null
        ? (game.format().usesCommanders() ? 1 : 0)
        : game.mulligans.freeMulligans;
  }

  static Game.OpeningHand progress(final Game game, final int seat) {
    return game.mulligans == null || game.mulligans.hands == null
        ? new Game.OpeningHand(0, false)
        : game.mulligans.hands.getOrDefault(seat, new Game.OpeningHand(0, false));
  }

  static int bottomCount(final Game game, final int seat) {
    return UNLIMITED.equals(mode(game)) || progress(game, seat).kept()
        ? 0
        : Math.min(
            zone(game, seat, HAND).size(),
            Math.max(0, progress(game, seat).redraws() - free(game)));
  }

  String configure(final Game game, final int seat, final Map<String, Object> data) {
    require(seat == game.host, "Only the host can configure mulligans");
    require("lobby".equals(game.status), "Mulligan settings are locked");
    final String mode = string(data, "mode", 20);
    require(Set.of(LONDON, UNLIMITED).contains(mode), "Unknown mulligan mode");
    require(data.containsKey("freeMulligans"), "Invalid freeMulligans");
    final int free = integer(data, "freeMulligans", 0, 0, 100);
    if (mode.equals(mode(game)) && free == free(game)) {
      return "mulligan settings unchanged";
    }
    final Game.MulliganState state = new Game.MulliganState();
    state.mode = mode;
    state.freeMulligans = free;
    game.mulligans = state;
    game.players.forEach(
        p -> {
          p.ready = false;
          p.acceptedWarnings = false;
        });
    return "changed mulligan settings";
  }

  static void begin(final Game game) {
    final Game.MulliganState state = new Game.MulliganState();
    state.mode = mode(game);
    state.freeMulligans = free(game);
    game.players.forEach(p -> state.hands.put(p.seat, new Game.OpeningHand(0, false)));
    game.mulligans = state;
    game.status = "opening";
  }

  String redraw(final Game game, final int seat, Map<String, Object> data) {
    final Game.OpeningHand progress = pending(game, seat);
    final List<Game.Card> hand = zone(game, seat, HAND);
    require(!hand.isEmpty(), "No cards to redraw");
    require(
        UNLIMITED.equals(mode(game)) || progress.redraws() - free(game) < hand.size(),
        "No further London mulligans");
    require(progress.redraws() < Integer.MAX_VALUE, "Too many mulligans");
    for (final Game.Card card : hand) {
      transitions.move(game, card, LIBRARY, false, false);
    }
    transitions.shuffle(game, seat);
    for (final Game.Card card : zone(game, seat, LIBRARY).stream().limit(7).toList()) {
      transitions.move(game, card, HAND, false, false);
    }
    game.mulligans.hands.put(seat, new Game.OpeningHand(progress.redraws() + 1, false));
    return "redrew an opening hand";
  }

  String keep(final Game game, final int seat, final Map<String, Object> data) {
    final Game.OpeningHand progress = pending(game, seat);
    final Object raw = data.getOrDefault("bottom", List.of());
    require(raw instanceof List<?>, "Invalid bottom cards");
    final List<?> ids = (List<?>) raw;
    require(
        ids.size() <= 7
            && ids.stream()
                .allMatch(
                    id ->
                        id instanceof String
                            && !((String) id).isBlank()
                            && ((String) id).length() <= 80),
        "Invalid bottom cards");
    require(
        ids.size() == bottomCount(game, seat),
        "Select exactly " + bottomCount(game, seat) + " cards to bottom");
    require(new HashSet<>(ids).size() == ids.size(), "Duplicate bottom card");
    final Map<String, Game.Card> hand = new HashMap<>();
    zone(game, seat, HAND).forEach(c -> hand.put(c.id, c));
    require(ids.stream().allMatch(hand::containsKey), "Choose cards from your current hand");
    final List<Game.Card> bottom = ids.stream().map(hand::get).toList();
    for (final Game.Card card : bottom) {
      transitions.move(game, card, LIBRARY, false, false);
    }
    game.mulligans.hands.put(seat, new Game.OpeningHand(progress.redraws(), true));
    if (game.players.stream().allMatch(p -> progress(game, p.seat).kept())) {
      game.status = "playing";
    }
    return "kept an opening hand";
  }

  private static Game.OpeningHand pending(final Game game, final int seat) {
    require("opening".equals(game.status), "Opening hands are not being chosen");
    final Game.OpeningHand progress = progress(game, seat);
    require(!progress.kept(), "Opening hand already kept");
    return progress;
  }
}
