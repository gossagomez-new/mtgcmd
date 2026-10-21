package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BoundedLibraryTest {
  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void lookRevealsOnlyTheSelectedTopCardsWithoutEditingOrFullSearch(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final List<String> ids = fixture.ids(0);
    final List<String> order = fixture.definitions(0);
    fixture.open(0, 0, "LOOK_TOP", 3);
    final JsonNode view = fixture.view(0);
    assertThat(view.path("search").isNull()).isTrue();
    for (int index = 0; index < ids.size(); index++) {
      final String id = ids.get(index);
      assertThat(view.path("cards").toString().contains(id)).isEqualTo(index < 3);
      assertThat(fixture.view(1).toString()).doesNotContain(id);
    }
    assertThat(fixture.view(1).path("libraryInspection").isNull()).isTrue();
    assertThat(view.toString()).doesNotContain("libraryInspections");
    view.path("cards")
        .forEach(
            card -> {
              if ("library".equals(card.path("zone").asText())) {
                assertThat(card.path("editable").asBoolean()).isFalse();
              }
            });
    fixture.reject(0, "MOVE", Map.of("cardId", ids.getFirst(), "zone", "hand"));
    assertThat(fixture.definitions(0)).isEqualTo(order);
    assertThat(fixture.game.log.getLast()).doesNotContain(ids.getFirst(), order.getFirst());
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void boundedAndFullSearchReplaceOnlyTheActorsCurrentSession(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    fixture.table.apply(0, "SEARCH", Map.of());
    final String first = fixture.open(0, 0, "LOOK_TOP", 2);
    final String second = fixture.open(0, 0, "SCRY", 1);
    assertThat(second).isNotEqualTo(first);
    fixture.reject(0, "CLOSE_INSPECTION", Map.of("inspectionId", first));
    fixture.table.apply(0, "SEARCH", Map.of());
    assertThat(fixture.view(0).path("libraryInspection").isNull()).isTrue();
    assertThat(fixture.view(0).path("search").asInt(-1)).isZero();
    fixture.reject(
        0, "APPLY_SCRY", Map.of("inspectionId", second, "top", List.of(), "bottom", List.of()));
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void grantsPermitOnlyTheDelegateAndRevocationImmediatelyHidesTheSession(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    fixture.reject(1, "LOOK_TOP", Map.of("target", 0, "count", 2));
    fixture.table.apply(0, "GRANT", Map.of("target", 1));
    final String id = fixture.open(1, 0, "SCRY", 2);
    assertThat(fixture.view(0).path("libraryInspection").isNull()).isTrue();
    for (final String cardId : fixture.ids(0).subList(0, 2)) {
      assertThat(fixture.view(1).path("cards").toString()).contains(cardId);
      assertThat(fixture.view(0).path("cards").toString()).doesNotContain(cardId);
    }
    fixture.table.apply(0, "GRANT", Map.of("target", 1, "allow", false));
    assertThat(fixture.view(1).path("libraryInspection").isNull()).isTrue();
    fixture.reject(
        1, "APPLY_SCRY", Map.of("inspectionId", id, "top", List.of(), "bottom", List.of()));
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void shortLibrariesClampSelectionAndEmptyLibrariesOrBadCountsRejectAtomically(
      final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final List<Game.Card> library = fixture.library(0);
    fixture.game.cards.removeAll(library.subList(2, library.size()));
    fixture.open(0, 0, "LOOK_TOP", 100);
    for (final String type : List.of("LOOK_TOP", "SCRY", "EXILE_TOP")) {
      for (final Object count : List.of(0, -1, 101, 1.5, "2", true)) {
        fixture.reject(0, type, Map.of("count", count));
      }
    }
    fixture.game.cards.removeAll(fixture.library(0));
    for (final String type : List.of("LOOK_TOP", "SCRY", "EXILE_TOP")) {
      fixture.reject(0, type, Map.of());
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void malformedStoredSessionsStayHiddenAndDoNotBreakLibraryMutations(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final List<String> selected = fixture.ids(0).subList(0, 2);
    final List<Game.LibraryInspection> malformed =
        Arrays.asList(
            new Game.LibraryInspection(null, 0, "look", selected),
            new Game.LibraryInspection("x".repeat(81), 0, "look", selected),
            new Game.LibraryInspection("session", -1, "look", selected),
            new Game.LibraryInspection("session", 99, "look", selected),
            new Game.LibraryInspection("session", 0, null, selected),
            new Game.LibraryInspection("session", 0, "invalid", selected),
            new Game.LibraryInspection("session", 0, "scry", null),
            new Game.LibraryInspection("session", 0, "scry", List.of()),
            new Game.LibraryInspection(
                "session", 0, "scry", List.of(selected.getFirst(), selected.getFirst())),
            null);
    for (final Game.LibraryInspection inspection : malformed) {
      fixture.game.libraryInspections.put(0, inspection);
      final JsonNode before = BoundedLibraryFixture.MAPPER.valueToTree(fixture.game);
      assertThat(fixture.view(0).path("libraryInspection").isNull()).isTrue();
      assertThat(BoundedLibraryFixture.MAPPER.<JsonNode>valueToTree(fixture.game))
          .isEqualTo(before);
    }
    fixture.table.apply(0, "DRAW", Map.of());
    fixture.game.libraryInspections = null;
    fixture.open(0, 0, "LOOK_TOP", 1);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void exileUsesCurrentTopOrderPublishesCardsAndInvalidatesInspection(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final List<String> before = fixture.definitions(0);
    final List<String> oldIds = fixture.ids(0);
    fixture.open(0, 0, "LOOK_TOP", 3);
    fixture.table.apply(0, "EXILE_TOP", Map.of("count", 2, "cardIds", List.of(oldIds.getLast())));
    assertThat(fixture.definitions(0)).isEqualTo(before.subList(2, before.size()));
    assertThat(fixture.view(0).path("libraryInspection").isNull()).isTrue();
    final List<Game.Card> exiled = GameLookup.zone(fixture.game, 0, "exile");
    assertThat(exiled)
        .extracting(card -> card.definition.id())
        .containsExactlyElementsOf(before.subList(0, 2));
    for (final Game.Card card : exiled) {
      assertThat(card.faceDown).isFalse();
      assertThat(oldIds).doesNotContain(card.id);
      assertThat(fixture.view(1).path("cards").toString()).contains(card.id, card.definition.id());
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void successfulLibraryMutationsInvalidateEveryAffectedBoundedSession(final GameFormat format) {
    for (final String type : List.of("DRAW", "SHUFFLE", "MOVE_IN", "MOVE_OUT")) {
      final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
      fixture.table.apply(0, "GRANT", Map.of("target", 1));
      fixture.open(0, 0, "LOOK_TOP", 2);
      fixture.open(1, 0, "SCRY", 2);
      if (type.equals("MOVE_IN")) {
        final Game.Card hand = GameLookup.zone(fixture.game, 0, "hand").getFirst();
        fixture.table.apply(0, "MOVE", Map.of("cardId", hand.id, "zone", "library"));
      } else if (type.equals("MOVE_OUT")) {
        fixture.table.apply(0, "SEARCH", Map.of());
        fixture.table.apply(0, "MOVE", Map.of("cardId", fixture.ids(0).getFirst(), "zone", "hand"));
      } else {
        fixture.table.apply(0, type, Map.of());
      }
      assertThat(fixture.view(0).path("libraryInspection").isNull()).isTrue();
      assertThat(fixture.view(1).path("libraryInspection").isNull()).isTrue();
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void concessionAndFinishRemoveSessionsWhileUnrelatedCommandsDoNot(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    fixture.open(0, 0, "SCRY", 2);
    fixture.table.apply(1, "COUNTER", Map.of("name", "life", "delta", -1));
    assertThat(fixture.view(0).path("libraryInspection").isObject()).isTrue();
    fixture.table.apply(0, "CONCEDE", Map.of());
    assertThat(fixture.view(0).path("libraryInspection").isNull()).isTrue();
    fixture.open(1, 1, "LOOK_TOP", 1);
    fixture.table.apply(0, "FINISH", Map.of());
    assertThat(fixture.view(1).path("libraryInspection").isNull()).isTrue();
    fixture.reject(1, "LOOK_TOP", Map.of());
  }
}
