package dev.mtgcmd.game;

import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Card;
import java.security.SecureRandom;
import java.util.*;

/** Physical transitions, including identity/reference cleanup and library ordering. */
final class CardZoneTransitions {
  private final SecureRandom random;

  CardZoneTransitions(final SecureRandom random) {
    this.random = random;
  }

  void move(
      final Game game,
      final Card card,
      final String destination,
      final boolean top,
      final boolean faceDown) {
    final String previousZone = card.zone;
    if (LIBRARY.equals(previousZone)) {
      LibraryInspections.invalidate(game, card.zoneOwner);
    }
    if (LIBRARY.equals(destination)) {
      LibraryInspections.invalidate(game, card.owner);
    }
    detachReferences(game, card.id);
    resetTransientState(card);
    assignDestination(card, destination, faceDown);
    if (Set.of(LIBRARY, HAND).contains(previousZone) || isPrivate(card)) {
      rekey(game, card);
    }
    game.cards.remove(card);
    if (card.token
        && !BATTLEFIELD.equals(Objects.requireNonNull(destination))
        && !STACK.equals(Objects.requireNonNull(destination))) {
      return;
    }
    insertAtDestination(game, card, destination, top);
  }

  private void detachReferences(final Game game, final String oldId) {
    for (final Card other : game.cards) {
      if (oldId.equals(other.attachedTo)) {
        other.attachedTo = null;
      }
      if (other.combat.equals("blocks:" + oldId)) {
        other.combat = "";
      }
    }
  }

  private void resetTransientState(final Card card) {
    card.attachedTo = null;
    card.combat = "";
    card.counters.clear();
    card.tapped = false;
    card.x = 0;
    card.y = 0;
    card.face = 0;
    card.revealedTo.clear();
  }

  private void assignDestination(
      final Card card, final String destination, final boolean faceDown) {
    card.zone = destination;
    card.faceDown = faceDown;
    if (!BATTLEFIELD.equals(Objects.requireNonNull(destination))
        && !STACK.equals(Objects.requireNonNull(destination))) {
      card.controller = card.owner;
    }
    card.zoneOwner =
        BATTLEFIELD.equals(Objects.requireNonNull(destination))
                || STACK.equals(Objects.requireNonNull(destination))
            ? card.controller
            : card.owner;
  }

  private void insertAtDestination(
      final Game game, final Card card, final String destination, final boolean top) {
    if (top && LIBRARY.equals(Objects.requireNonNull(destination))) {
      int index = 0;
      while (index < game.cards.size()
          && !(LIBRARY.equals(Objects.requireNonNull(game.cards.get(index).zone))
              && game.cards.get(index).zoneOwner == card.zoneOwner)) {
        index++;
      }
      game.cards.add(index, card);
    } else {
      game.cards.add(card);
    }
  }

  void shuffle(final Game game, final int owner) {
    final List<Card> library = zone(game, owner, LIBRARY);
    Collections.shuffle(library, random);
    reorderLibrary(game, owner, library);
  }

  void reorderLibrary(final Game game, final int owner, final List<Card> library) {
    game.cards.removeAll(zone(game, owner, LIBRARY));
    for (final Card c : library) {
      rekey(game, c);
      c.revealedTo.clear();
    }
    game.cards.addAll(library);
    game.searches.entrySet().removeIf(e -> e.getValue() == owner);
    LibraryInspections.invalidate(game, owner);
  }

  private void rekey(final Game game, final Card card) {
    final String old = card.id;
    card.id = UUID.randomUUID().toString();
    game.cards.forEach(
        other -> {
          if (old.equals(other.attachedTo)) {
            other.attachedTo = null;
          }
        });
  }
}
