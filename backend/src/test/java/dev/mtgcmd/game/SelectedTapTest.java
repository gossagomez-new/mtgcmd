package dev.mtgcmd.game;

import static dev.mtgcmd.game.GameZones.*;
import static dev.mtgcmd.game.PermanentActionFixture.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SelectedTapTest {
  private static final String COMMAND = "TAP_SELECTED";
  private static final String IDS = "cardIds";
  private static final String TAPPED = "tapped";

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void setsMixedStatesWithOneRevisionAndLogAndPreservesUnselected(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card first = fixture.card(0, BATTLEFIELD);
    final Game.Card second = fixture.card(1, BATTLEFIELD);
    second.controller = 0;
    second.zoneOwner = 0;
    second.tapped = true;
    final Game.Card untouched = fixture.card(0, BATTLEFIELD);
    for (final boolean tapped : List.of(true, true, false)) {
      fixture.apply(0, COMMAND, Map.of(IDS, List.of(first.id, second.id), TAPPED, tapped));
      assertThat(first.tapped).isEqualTo(tapped);
      assertThat(second.tapped).isEqualTo(tapped);
      assertThat(untouched.tapped).isFalse();
    }
    assertThat(fixture.game.log.getLast()).isEqualTo("Player 0: untap selected");
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void validatesEveryCardBeforeMutation(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card first = fixture.card(0, BATTLEFIELD);
    final Game.Card foreign = fixture.card(1, BATTLEFIELD);
    final Game.Card hand = fixture.card(0, HAND);
    fixture.reject(
        0, COMMAND, Map.of(IDS, List.of(first.id, foreign.id), TAPPED, true), PERMISSION_ERROR);
    fixture.reject(
        0, COMMAND, Map.of(IDS, List.of(first.id, hand.id), TAPPED, true), BATTLEFIELD_ERROR);
    assertThat(first.tapped).isFalse();
    assertThat(foreign.tapped).isFalse();
    assertThat(hand.tapped).isFalse();
    assertThat(fixture.game.log.toString()).doesNotContain(first.definition.name());
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void rejectsMalformedPayloadsWithoutMutation(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card first = fixture.card(0, BATTLEFIELD);
    final List<Object> invalid = new ArrayList<>();
    invalid.add("not a list");
    invalid.add(List.of());
    invalid.add(Collections.nCopies(501, first.id));
    invalid.add(List.of(first.id, first.id));
    invalid.add(List.of(first.id, " " + first.id));
    invalid.add(List.of(first.id, 17));
    invalid.add(List.of(first.id, " "));
    invalid.add(List.of(first.id, "x".repeat(81)));
    invalid.add(Arrays.asList(first.id, null));
    for (final Object ids : invalid) {
      fixture.reject(0, COMMAND, Map.of(IDS, ids, TAPPED, true), "Invalid card selection");
      assertThat(first.tapped).isFalse();
    }
    fixture.reject(0, COMMAND, Map.of(TAPPED, true), "Invalid card selection");
    for (final Map<String, Object> data :
        List.of(
            Map.<String, Object>of(IDS, List.of(first.id)),
            Map.<String, Object>of(IDS, List.of(first.id), TAPPED, "true"))) {
      fixture.reject(0, COMMAND, data, "tapped must be true or false");
      assertThat(first.tapped).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void acceptsTheSelectionLimitAndRejectsStaleRevisions(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final List<String> ids = new ArrayList<>();
    for (int index = 0; index < 500; index++) {
      ids.add(fixture.card(0, BATTLEFIELD).id);
    }
    fixture.apply(0, COMMAND, Map.of(IDS, ids, TAPPED, true));
    final long revision = fixture.game.revision;
    assertThatThrownBy(
            () ->
                fixture.engine.apply(
                    fixture.game,
                    0,
                    new Game.Command(
                        "stale", revision - 1, COMMAND, Map.of(IDS, ids, TAPPED, false))))
        .hasMessage("Stale revision");
    assertThat(fixture.game.cards).allMatch(card -> card.tapped);
    assertThat(fixture.game.revision).isEqualTo(revision);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void admissionAndMissingCardsRejectWithoutChanges(final GameFormat format) {
    final PermanentActionFixture fixture = new PermanentActionFixture(format);
    final Game.Card card = fixture.card(0, BATTLEFIELD);
    fixture.reject(
        0, COMMAND, Map.of(IDS, List.of(card.id, "missing"), TAPPED, true), "Card unavailable");
    for (final String status : List.of("lobby", "finished")) {
      fixture.game.status = status;
      fixture.reject(
          0, COMMAND, Map.of(IDS, List.of(card.id), TAPPED, true), "Game is not playing");
    }
    fixture.game.status = "playing";
    fixture.game.players.getFirst().conceded = true;
    fixture.reject(0, COMMAND, Map.of(IDS, List.of(card.id), TAPPED, true), "You have conceded");
    assertThat(card.tapped).isFalse();
  }
}
