package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class CardZoneTransitionsTest {
  private final CardZoneTransitions transitions = new CardZoneTransitions(new SecureRandom());

  static Stream<Arguments> moves() {
    return GameEngine.ZONES.stream()
        .flatMap(from -> GameEngine.ZONES.stream().map(to -> Arguments.of(from, to)));
  }

  @ParameterizedTest
  @MethodSource("moves")
  void transitionsPreserveResetOwnershipReferenceCleanupAndHiddenIdRules(String from, String to) {
    Game game = new Game();
    Game.Card card = new Game.Card();
    card.owner = 0;
    card.controller = 1;
    card.zoneOwner = 1;
    card.zone = from;
    card.tapped = true;
    card.face = 1;
    card.x = .5;
    card.y = .7;
    card.counters.put("charge", 3);
    card.revealedTo.add(1);
    card.attachedTo = "old attachment";
    card.combat = "attacker";
    Game.Card dependent = new Game.Card();
    dependent.attachedTo = card.id;
    dependent.combat = "blocks:" + card.id;
    game.cards.addAll(List.of(card, dependent));
    String oldId = card.id;
    transitions.move(game, card, to, false, true);
    assertThat(card.zone).isEqualTo(to);
    assertThat(card.owner).isZero();
    assertThat(card.controller).isEqualTo(Set.of("battlefield", "stack").contains(to) ? 1 : 0);
    assertThat(card.zoneOwner).isEqualTo(card.controller);
    assertThat(card.faceDown).isTrue();
    assertThat(card.tapped).isFalse();
    assertThat(card.face).isZero();
    assertThat(card.x).isZero();
    assertThat(card.y).isZero();
    assertThat(card.counters).isEmpty();
    assertThat(card.revealedTo).isEmpty();
    assertThat(card.attachedTo).isNull();
    assertThat(card.combat).isEmpty();
    assertThat(dependent.attachedTo).isNull();
    assertThat(dependent.combat).isEmpty();
    boolean hiddenTransition =
        Set.of("library", "hand").contains(from) || Set.of("library", "hand").contains(to);
    if (hiddenTransition) assertThat(card.id).isNotEqualTo(oldId);
    else assertThat(card.id).isEqualTo(oldId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"library", "hand", "battlefield", "graveyard", "exile", "command", "stack"})
  void tokensRemainOnlyOnBattlefieldOrStack(String destination) {
    Game game = new Game();
    Game.Card token = new Game.Card();
    token.zone = "battlefield";
    token.token = true;
    game.cards.add(token);
    transitions.move(game, token, destination, false, false);
    assertThat(game.cards.contains(token))
        .isEqualTo(Set.of("battlefield", "stack").contains(destination));
  }

  @Test
  void libraryPlacementAndShufflePreserveOtherSeatsAndInvalidateSearchesAndReveals() {
    Game game = new Game();
    Game.Card other = new Game.Card();
    other.zone = "library";
    other.owner = other.controller = other.zoneOwner = 1;
    Game.Card first = new Game.Card();
    first.zone = "library";
    Game.Card incoming = new Game.Card();
    incoming.zone = "hand";
    game.cards.addAll(List.of(other, first, incoming));
    transitions.move(game, incoming, "library", true, false);
    assertThat(game.cards).containsExactly(other, incoming, first);
    transitions.move(game, incoming, "library", false, false);
    assertThat(game.cards).containsExactly(other, first, incoming);
    game.searches.put(0, 0);
    game.searches.put(1, 0);
    game.searches.put(2, 1);
    first.revealedTo.add(1);
    incoming.revealedTo.add(1);
    Set<String> oldIds = Set.of(first.id, incoming.id);
    String otherId = other.id;
    transitions.shuffle(game, 0);
    assertThat(List.of(first.id, incoming.id)).doesNotContainAnyElementsOf(oldIds);
    assertThat(other.id).isEqualTo(otherId);
    assertThat(first.revealedTo).isEmpty();
    assertThat(incoming.revealedTo).isEmpty();
    assertThat(game.searches).containsExactlyEntriesOf(Map.of(2, 1));
    assertThat(game.cards).containsExactlyInAnyOrder(other, first, incoming);
  }
}
