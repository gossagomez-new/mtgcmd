package dev.mtgcmd.game;

import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Card;
import java.security.SecureRandom;
import java.util.*;

/** Physical transitions, including identity/reference cleanup and library ordering. */
final class CardZoneTransitions {
  private final SecureRandom random;

  CardZoneTransitions(SecureRandom random) {
    this.random = random;
  }

  void move(Game game, Card card, String destination, boolean top, boolean faceDown) {
    String previousZone = card.zone;
    detachReferences(game, card.id);
    resetTransientState(card);
    assignDestination(card, destination, faceDown);
    if (Set.of(LIBRARY, HAND).contains(previousZone) || isPrivate(card)) rekey(game, card);
    game.cards.remove(card);
    if (card.token && !destination.equals(BATTLEFIELD) && !destination.equals(STACK)) return;
    insertAtDestination(game, card, destination, top);
  }

  private void detachReferences(Game game, String oldId) {
    for (Card other : game.cards) {
      if (oldId.equals(other.attachedTo)) other.attachedTo = null;
      if (other.combat.equals("blocks:" + oldId)) other.combat = "";
    }
  }

  private void resetTransientState(Card card) {
    card.attachedTo = null;
    card.combat = "";
    card.counters.clear();
    card.tapped = false;
    card.x = 0;
    card.y = 0;
    card.face = 0;
    card.revealedTo.clear();
  }

  private void assignDestination(Card card, String destination, boolean faceDown) {
    card.zone = destination;
    card.faceDown = faceDown;
    if (!destination.equals(BATTLEFIELD) && !destination.equals(STACK))
      card.controller = card.owner;
    card.zoneOwner =
        destination.equals(BATTLEFIELD) || destination.equals(STACK) ? card.controller : card.owner;
  }

  private void insertAtDestination(Game game, Card card, String destination, boolean top) {
    if (top && destination.equals(LIBRARY)) {
      int index = 0;
      while (index < game.cards.size()
          && !(game.cards.get(index).zone.equals(LIBRARY)
              && game.cards.get(index).zoneOwner == card.zoneOwner)) index++;
      game.cards.add(index, card);
    } else game.cards.add(card);
  }

  void shuffle(Game g, int owner) {
    List<Card> library = zone(g, owner, LIBRARY);
    Collections.shuffle(library, random);
    g.cards.removeAll(library);
    for (Card c : library) {
      rekey(g, c);
      c.revealedTo.clear();
    }
    g.cards.addAll(library);
    g.searches.entrySet().removeIf(e -> e.getValue() == owner);
  }

  private void rekey(Game g, Card c) {
    String old = c.id;
    c.id = UUID.randomUUID().toString();
    g.cards.forEach(
        other -> {
          if (old.equals(other.attachedTo)) other.attachedTo = null;
        });
  }
}
