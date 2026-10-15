package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Card;
import java.util.*;

/** Shared policies for object visibility, definition inspection, and editing. */
final class GameAccess {
  boolean visible(final Game game, final int seat, final Card card) {
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
        || Objects.equals(game.searches.get(seat), card.zoneOwner);
  }

  boolean canSeeDefinition(final Game game, final int seat, final Card card) {
    return visible(game, seat, card)
        && (!card.faceDown || card.controller == seat || card.revealedTo.contains(seat));
  }

  boolean mayEdit(final Game game, final int seat, final Card card) {
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
