package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Card;
import java.util.*;

/** Shared policies for object visibility, definition inspection, and editing. */
final class GameAccess {
  boolean visible(final Game game, final int seat, final Card card) {
    if ("opening".equals(game.status) && isPrivate(card)) {
      return HAND.equals(card.zone) && card.zoneOwner == seat;
    }
    if (!isPrivate(card)) {
      return true;
    }
    if (card.revealedTo.contains(seat)) {
      return true;
    }
    if (!canAccessPrivate(game, seat, card.zoneOwner)) {
      return false;
    }
    return !LIBRARY.equals(Objects.requireNonNull(card.zone))
        || Objects.equals(game.searches.get(seat), card.zoneOwner)
        || boundedVisibility(game, seat, card);
  }

  Game.LibraryInspection libraryInspection(final Game game, final int seat) {
    final Game.LibraryInspection inspection = LibraryInspections.current(game, seat);
    return inspection != null
            && "playing".equals(game.status)
            && !player(game, seat).conceded
            && !player(game, inspection.target()).conceded
            && canAccessPrivate(game, seat, inspection.target())
        ? inspection
        : null;
  }

  private boolean boundedVisibility(final Game game, final int seat, final Card card) {
    final Game.LibraryInspection inspection = libraryInspection(game, seat);
    return inspection != null
        && inspection.target() == card.zoneOwner
        && inspection.cardIds().contains(card.id);
  }

  boolean canSeeDefinition(final Game game, final int seat, final Card card) {
    return visible(game, seat, card)
        && (!card.faceDown || card.controller == seat || card.revealedTo.contains(seat));
  }

  boolean mayEdit(final Game game, final int seat, final Card card) {
    if ("opening".equals(game.status)) {
      return false;
    }
    if (player(game, seat).conceded) {
      return false;
    }
    if (isPrivate(card)) {
      return canAccessPrivate(game, seat, card.zoneOwner)
          && (!LIBRARY.equals(Objects.requireNonNull(card.zone))
              || Objects.equals(game.searches.get(seat), card.zoneOwner));
    }
    return card.controller == seat;
  }

  Card editable(final Game game, final int seat, final Map<String, Object> data) {
    final Card card = find(game, string(data, "cardId", 80));
    if (LIBRARY.equals(Objects.requireNonNull(card.zone))
        && canAccessPrivate(game, seat, card.zoneOwner)) {
      require(
          Objects.equals(game.searches.get(seat), card.zoneOwner), "Open a library search first");
    }
    require(mayEdit(game, seat, card), "No permission to change this card");
    return card;
  }

  boolean canAccessPrivate(final Game game, final int seat, final int owner) {
    return seat == owner || game.grants.getOrDefault(owner, Set.of()).contains(seat);
  }

  void privatePermission(final Game game, final int seat, final int owner) {
    player(game, owner);
    require(canAccessPrivate(game, seat, owner), "No permission for this zone");
  }
}
