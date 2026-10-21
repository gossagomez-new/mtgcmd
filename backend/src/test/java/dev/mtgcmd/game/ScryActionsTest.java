package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ScryActionsTest {
  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void orderedPartitionIsAtomicAndPreservesUninspectedCardsAndOtherZones(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final List<String> before = fixture.definitions(0);
    final List<String> ids = fixture.ids(0);
    final JsonNode otherZones = otherZones(fixture);
    final String session = fixture.open(0, 0, "SCRY", 4);
    fixture.commit(0, session, List.of(ids.get(2), ids.get(0)), List.of(ids.get(3), ids.get(1)));
    final List<String> expected = new ArrayList<>(List.of(before.get(2), before.get(0)));
    expected.addAll(before.subList(4, before.size()));
    expected.addAll(List.of(before.get(3), before.get(1)));
    assertThat(fixture.definitions(0)).containsExactlyElementsOf(expected);
    assertThat(otherZones(fixture)).isEqualTo(otherZones);
    assertThat(fixture.view(0).path("libraryInspection").isNull()).isTrue();
    for (final String old : ids) {
      assertThat(fixture.ids(0)).doesNotContain(old);
    }
    assertThat(fixture.game.log.getLast()).contains("scried 4");
    for (final String definition : before) {
      assertThat(fixture.game.log.getLast()).doesNotContain(definition);
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void allTopAllBottomAndWholeLibraryOrdersWork(final GameFormat format) {
    for (final boolean bottom : List.of(false, true)) {
      final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
      final List<String> before = new ArrayList<>(fixture.definitions(0));
      final List<String> ids = new ArrayList<>(fixture.ids(0));
      final String session = fixture.open(0, 0, "SCRY", 100);
      Collections.reverse(ids);
      Collections.reverse(before);
      fixture.commit(0, session, bottom ? List.of() : ids, bottom ? ids : List.of());
      assertThat(fixture.definitions(0)).isEqualTo(before);
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void cancelPreservesExactCardsAndRejectsStaleOrForeignInspectionIds(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final JsonNode before = BoundedLibraryFixture.MAPPER.valueToTree(fixture.game.cards);
    final String session = fixture.open(0, 0, "SCRY", 3);
    fixture.reject(1, "CLOSE_INSPECTION", Map.of("inspectionId", session));
    fixture.reject(
        0, "APPLY_SCRY", Map.of("inspectionId", "other", "top", List.of(), "bottom", List.of()));
    fixture.table.apply(0, "CLOSE_INSPECTION", Map.of("inspectionId", session));
    assertThat(BoundedLibraryFixture.MAPPER.<JsonNode>valueToTree(fixture.game.cards))
        .isEqualTo(before);
    assertThat(fixture.view(0).path("libraryInspection").isNull()).isTrue();
    fixture.reject(0, "CLOSE_INSPECTION", Map.of("inspectionId", session));
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void invalidPartitionsAndWrongModesNeverChangeState(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final String session = fixture.open(0, 0, "SCRY", 2);
    final List<String> ids = fixture.ids(0);
    final List<Map<String, Object>> invalid =
        List.of(
            Map.of("inspectionId", session, "top", List.of(ids.get(0)), "bottom", List.of()),
            Map.of(
                "inspectionId", session, "top", List.of(ids.get(0)), "bottom", List.of(ids.get(0))),
            Map.of(
                "inspectionId", session, "top", List.of(ids.get(0)), "bottom", List.of(ids.get(2))),
            Map.of("inspectionId", session, "top", List.of(1), "bottom", List.of(ids.get(1))),
            Map.of("inspectionId", session, "top", "not a list", "bottom", List.of()),
            Map.of("inspectionId", session, "top", List.of(), "bottom", List.of()),
            Map.of("inspectionId", session, "top", List.of(ids.get(0))),
            Map.of("inspectionId", session, "top", List.of(" ", ids.get(1)), "bottom", List.of()));
    for (final Map<String, Object> data : invalid) {
      fixture.reject(0, "APPLY_SCRY", data);
    }
    fixture.reject(
        1,
        "APPLY_SCRY",
        Map.of("inspectionId", session, "top", ids.subList(0, 2), "bottom", List.of()));
    final String look = fixture.open(0, 0, "LOOK_TOP", 2);
    fixture.reject(
        0,
        "APPLY_SCRY",
        Map.of("inspectionId", look, "top", ids.subList(0, 2), "bottom", List.of()));
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void scryClosesSearchesAndRevokesLibraryRevealsWithoutLeakingOrder(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    fixture.table.apply(0, "GRANT", Map.of("target", 1));
    fixture.table.apply(1, "SEARCH", Map.of("target", 0));
    final List<String> ids = fixture.ids(0);
    fixture.library(0).getFirst().revealedTo.add(1);
    final String session = fixture.open(0, 0, "SCRY", 2);
    fixture.commit(0, session, List.of(ids.get(1)), List.of(ids.getFirst()));
    assertThat(fixture.view(1).path("search").isNull()).isTrue();
    for (final Game.Card card : fixture.library(0)) {
      assertThat(card.revealedTo).isEmpty();
      assertThat(fixture.view(1).path("cards").toString()).doesNotContain(card.id);
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void sessionSurvivesJsonRoundTripAndLegacySnapshotsHaveNoSession(final GameFormat format)
      throws Exception {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final String session = fixture.open(0, 0, "SCRY", 2);
    final List<String> selected = List.copyOf(fixture.ids(0).subList(0, 2));
    final ObjectNode saved = BoundedLibraryFixture.MAPPER.valueToTree(fixture.game);
    final Game restored = BoundedLibraryFixture.MAPPER.treeToValue(saved, Game.class);
    assertThat(fixture.table.engine.view(restored, 0).get("libraryInspection")).isNotNull();
    fixture.table.engine.apply(
        restored,
        0,
        new Game.Command(
            "restored",
            restored.revision,
            "APPLY_SCRY",
            Map.of("inspectionId", session, "top", selected, "bottom", List.of())));
    saved.remove("libraryInspections");
    final Game legacy = BoundedLibraryFixture.MAPPER.treeToValue(saved, Game.class);
    assertThat(fixture.table.engine.view(legacy, 0).get("libraryInspection")).isNull();
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void invalidSavedPrefixesAreHiddenWithoutMutatingTheGame(final GameFormat format) {
    final BoundedLibraryFixture fixture = new BoundedLibraryFixture(format);
    final String session = fixture.open(0, 0, "SCRY", 2);
    final List<String> ids = fixture.ids(0);
    Collections.swap(
        fixture.game.cards,
        fixture.game.cards.indexOf(fixture.library(0).get(0)),
        fixture.game.cards.indexOf(fixture.library(0).get(1)));
    final JsonNode before = BoundedLibraryFixture.MAPPER.valueToTree(fixture.game);
    assertThat(fixture.view(0).path("libraryInspection").isNull()).isTrue();
    assertThat(BoundedLibraryFixture.MAPPER.<JsonNode>valueToTree(fixture.game)).isEqualTo(before);
    fixture.reject(
        0,
        "APPLY_SCRY",
        Map.of("inspectionId", session, "top", ids.subList(0, 2), "bottom", List.of()));
  }

  private static JsonNode otherZones(final BoundedLibraryFixture fixture) {
    return BoundedLibraryFixture.MAPPER.valueToTree(
        fixture.game.cards.stream()
            .filter(card -> !card.zone.equals("library") || card.zoneOwner != 0)
            .toList());
  }
}
