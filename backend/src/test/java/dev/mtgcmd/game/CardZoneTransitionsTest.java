package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class CardZoneTransitionsTest {
  private static final String BATTLEFIELD_ZONE = "battlefield";

  private static final String HAND_ZONE = "hand";

  private static final String LIBRARY_ZONE = "library";

  private final CardZoneTransitions transitions = new CardZoneTransitions(new SecureRandom());

  static Stream<Arguments> moves() {
    return GameEngine.ZONES.stream()
        .flatMap(from -> GameEngine.ZONES.stream().map(to -> Arguments.of(from, to)));
  }

  @ParameterizedTest
  @MethodSource("moves")
  void transitionsPreserveResetOwnershipReferenceCleanupAndHiddenIdRules(
      final String from, final String destination) {
    final Game game = new Game();
    final Game.Card card = new Game.Card();
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
    final Game.Card dependent = new Game.Card();
    dependent.attachedTo = card.id;
    dependent.combat = "blocks:" + card.id;
    game.cards.addAll(List.of(card, dependent));
    final String oldId = card.id;
    transitions.move(game, card, destination, false, true);
    assertThat(card.zone).isEqualTo(destination);
    assertThat(card.owner).isZero();
    assertThat(card.controller)
        .isEqualTo(Set.of(BATTLEFIELD_ZONE, "stack").contains(destination) ? 1 : 0);
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
    final boolean hiddenTransition =
        Set.of(LIBRARY_ZONE, HAND_ZONE).contains(from)
            || Set.of(LIBRARY_ZONE, HAND_ZONE).contains(destination);
    if (hiddenTransition) {
      assertThat(card.id).isNotEqualTo(oldId);
    } else {
      assertThat(card.id).isEqualTo(oldId);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        LIBRARY_ZONE,
        HAND_ZONE,
        BATTLEFIELD_ZONE,
        "graveyard",
        "exile",
        "command",
        "stack"
      })
  void tokensRemainOnlyOnBattlefieldOrStack(final String destination) {
    final Game game = new Game();
    final Game.Card token = new Game.Card();
    token.zone = BATTLEFIELD_ZONE;
    token.token = true;
    game.cards.add(token);
    transitions.move(game, token, destination, false, false);
    assertThat(game.cards.contains(token))
        .isEqualTo(Set.of(BATTLEFIELD_ZONE, "stack").contains(destination));
  }

  @Test
  void libraryPlacementAndShufflePreserveOtherSeatsAndInvalidateSearchesAndReveals() {
    final Game game = new Game();
    final Game.Card other = new Game.Card();
    other.zone = LIBRARY_ZONE;
    other.owner = other.controller = other.zoneOwner = 1;
    final Game.Card first = new Game.Card();
    first.zone = LIBRARY_ZONE;
    final Game.Card incoming = new Game.Card();
    incoming.zone = HAND_ZONE;
    game.cards.addAll(List.of(other, first, incoming));
    transitions.move(game, incoming, LIBRARY_ZONE, true, false);
    assertThat(game.cards).containsExactly(other, incoming, first);
    transitions.move(game, incoming, LIBRARY_ZONE, false, false);
    assertThat(game.cards).containsExactly(other, first, incoming);
    game.searches.put(0, 0);
    game.searches.put(1, 0);
    game.searches.put(2, 1);
    first.revealedTo.add(1);
    incoming.revealedTo.add(1);
    final Set<String> oldIds = Set.of(first.id, incoming.id);
    final String otherId = other.id;
    transitions.shuffle(game, 0);
    assertThat(List.of(first.id, incoming.id)).doesNotContainAnyElementsOf(oldIds);
    assertThat(other.id).isEqualTo(otherId);
    assertThat(first.revealedTo).isEmpty();
    assertThat(incoming.revealedTo).isEmpty();
    assertThat(game.searches).containsExactlyEntriesOf(Map.of(2, 1));
    assertThat(game.cards).containsExactlyInAnyOrder(other, first, incoming);
  }
}
