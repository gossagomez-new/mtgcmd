package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.Game.*;
import dev.mtgcmd.testsupport.OpeningHands;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StandardGameTest {
  private static final String CREATURE_TYPE = "Creature";

  private static final String READY_COMMAND = "READY";

  private static final String START_COMMAND = "START";

  private static final String CARD_ID_FIELD = "cardId";

  private static final String FIRST_PLAYER_KEY = "firstPlayer";

  private static final String LEGAL_STATUS = "legal";

  private static final String TARGET_FIELD = "target";

  private final GameEngine engine = new GameEngine();

  private Game standard() {
    final Game game = new Game();
    game.format = GameFormat.STANDARD;
    engine.join(game, "A");
    engine.join(game, "B");
    return game;
  }

  private Definition card(
      final String identifier,
      final String type,
      final String text,
      final String legality,
      final Long checkedAt) {
    return new Definition(
        identifier,
        identifier,
        "",
        text,
        type,
        List.of("R"),
        false,
        List.of(),
        legality == null ? Map.of() : Map.of("standard", legality),
        checkedAt);
  }

  private Definition island() {
    return card("Island", "Basic Land — Island", "", LEGAL_STATUS, System.currentTimeMillis());
  }

  private void action(
      final Game game, final int seat, final String type, final Map<String, Object> data) {
    engine.apply(game, seat, new Command(UUID.randomUUID().toString(), game.revision, type, data));
  }

  private Game ready() {
    final Game game = standard();
    for (int seat = 0; seat < 2; seat++) {
      engine.load(game, seat, Collections.nCopies(60, island()), List.of());
      action(game, seat, READY_COMMAND, Map.of("acceptWarnings", true));
    }
    return game;
  }

  @Test
  void standardHasTwoTwentyLifeSeats() {
    final Game game = standard();
    assertThat(game.players)
        .allSatisfy(player -> assertThat(player.counters.get("life")).isEqualTo(20));
    assertThatThrownBy(() -> engine.join(game, "C")).hasMessage("Room is full");
    assertThat(engine.view(game, 0))
        .containsEntry("format", "STANDARD")
        .containsEntry("seatCapacity", 2);
  }

  @ParameterizedTest
  @ValueSource(ints = {60, 61, 250})
  void acceptsDeckSizeBoundariesWithoutCommanders(final int count) {
    final Game game = standard();
    engine.load(game, 0, Collections.nCopies(count, island()), null);
    assertThat(game.players.getFirst().deck).hasSize(count);
    assertThat(game.players.getFirst().commanders).isEmpty();
    assertThat(game.players.getFirst().warnings).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 59, 251})
  void rejectsDeckSizeBoundaries(final int count) {
    final Game game = standard();
    assertThatThrownBy(() -> engine.load(game, 0, Collections.nCopies(count, island()), List.of()))
        .hasMessageContaining("60–250");
    assertThat(game.players.getFirst().deck).isEmpty();
  }

  @Test
  void rejectsCommandersAndWarnsAboutCopiesAndLegalityWithoutCommanderRules() {
    final Game game = standard();
    assertThatThrownBy(
            () -> engine.load(game, 0, Collections.nCopies(60, island()), List.of("Island")))
        .hasMessageContaining("does not use commanders");
    final List<Definition> deck = new ArrayList<>(Collections.nCopies(55, island()));
    deck.addAll(
        Collections.nCopies(5, card("Spell", "Sorcery", "", "banned", System.currentTimeMillis())));
    engine.load(game, 0, deck, List.of());
    assertThat(game.players.getFirst().warnings)
        .anyMatch(w -> w.contains("not listed as Standard legal"))
        .anyMatch(w -> w.contains("four copies"))
        .noneMatch(
            w ->
                w.contains("Commander") || w.contains("singleton") || w.contains("color identity"));
    assertThatThrownBy(() -> action(game, 0, READY_COMMAND, Map.of()))
        .hasMessageContaining("Accept deck warnings");
    action(game, 0, READY_COMMAND, Map.of("acceptWarnings", true));
    assertThat(game.players.getFirst().ready).isTrue();
  }

  @Test
  void anyNumberExceptionAndFourCopiesAreAllowed() {
    final Game game = standard();
    final List<Definition> deck = new ArrayList<>(Collections.nCopies(52, island()));
    deck.addAll(
        Collections.nCopies(
            4, card("Normal", CREATURE_TYPE, "", LEGAL_STATUS, System.currentTimeMillis())));
    deck.addAll(
        Collections.nCopies(
            4,
            card(
                "Exception",
                CREATURE_TYPE,
                "A deck can have any number of cards named Exception.",
                LEGAL_STATUS,
                System.currentTimeMillis())));
    engine.load(game, 0, deck, List.of());
    assertThat(game.players.getFirst().warnings).isEmpty();
    engine.load(
        game,
        0,
        Collections.nCopies(
            60,
            card(
                "Exception",
                CREATURE_TYPE,
                "A deck can have any number of cards named Exception.",
                LEGAL_STATUS,
                System.currentTimeMillis())),
        List.of());
    assertThat(game.players.getFirst().warnings).isEmpty();
  }

  @Test
  void missingStaleAndFutureLegalityRequireVerificationWarnings() {
    for (final Long time :
        Arrays.asList(
            null,
            System.currentTimeMillis() - 86_400_001,
            System.currentTimeMillis() + 86_400_000)) {
      final Game game = standard();
      engine.load(
          game,
          0,
          Collections.nCopies(60, card("Island", "Basic Land", "", LEGAL_STATUS, time)),
          List.of());
      assertThat(game.players.getFirst().warnings)
          .anyMatch(w -> w.contains("could not be verified"));
    }
  }

  @Test
  void hostChoosesStartingPlayerAndTurnsAlternateWithSevenCards() {
    final Game game = ready();
    assertThatThrownBy(() -> action(game, 1, START_COMMAND, Map.of())).hasMessageContaining("host");
    assertThatThrownBy(() -> action(game, 0, START_COMMAND, Map.of(FIRST_PLAYER_KEY, 2)))
        .hasMessageContaining(FIRST_PLAYER_KEY);
    action(game, 0, START_COMMAND, Map.of(FIRST_PLAYER_KEY, 1));
    assertThat(game.active).isEqualTo(1);
    assertThat(game.cards).noneMatch(c -> "command".equals(Objects.requireNonNull(c.zone)));
    for (int seat = 0; seat < 2; seat++) {
      final int owner = seat;
      assertThat(
              game.cards.stream()
                  .filter(c -> c.owner == owner && "hand".equals(Objects.requireNonNull(c.zone))))
          .hasSize(7);
      assertThat(
              game.cards.stream()
                  .filter(
                      c -> c.owner == owner && "library".equals(Objects.requireNonNull(c.zone))))
          .hasSize(53);
    }
    OpeningHands.keepAll(engine, game);
    game.phase = "Cleanup";
    action(game, 1, "PHASE", Map.of());
    assertThat(game.active).isZero();
    game.phase = "Cleanup";
    action(game, 0, "PHASE", Map.of());
    assertThat(game.active).isEqualTo(1);
    assertThatThrownBy(() -> action(game, 0, "GRANT", Map.of(TARGET_FIELD, 2)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> action(game, 0, "DRAW", Map.of(TARGET_FIELD, 1)))
        .hasMessageContaining("permission");
    action(game, 1, "CONCEDE", Map.of());
    assertThat(game.active).isZero();
  }

  @Test
  void twoReadySeatsAreMandatoryAndOmittedFirstPlayerDefaultsToHost() {
    final Game game = standard();
    assertThatThrownBy(() -> action(game, 0, START_COMMAND, Map.of()))
        .hasMessageContaining("ready");
    final Game started = ready();
    action(started, 0, START_COMMAND, Map.of());
    assertThat(started.active).isZero();
  }

  @Test
  void legacyJsonDefaultsToCommanderAndPreservesCountersAndDefinitions() throws Exception {
    final ObjectMapper mapper = new ObjectMapper();
    final Game game =
        mapper.readValue(
            "{\"status\":\"playing\",\"players\":[{\"seat\":0,\"counters\":{\"life\":17}}]}",
            Game.class);
    assertThat(game.format()).isEqualTo(GameFormat.COMMANDER);
    assertThat(game.players.getFirst().counters.get("life")).isEqualTo(17);
    final Definition def =
        mapper.readValue(
            "{\"id\":\"island\",\"name\":\"Island\",\"image\":\"\",\"text\":\"\",\"type\":\"Basic Land\",\"colors\":[],\"legal\":true,\"faces\":[]}",
            Definition.class);
    assertThat(def.legal()).isTrue();
    assertThat(def.legalities()).isEmpty();
    assertThat(def.legalityCheckedAt()).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 2, 3})
  void invalidTargetsCannotGrantSearchDrawControlOrReveal(final int target) {
    final Game game = ready();
    action(game, 0, START_COMMAND, Map.of());
    OpeningHands.keepAll(engine, game);
    for (final String type : List.of("DRAW", "SEARCH", "GRANT")) {
      assertThatThrownBy(() -> action(game, 0, type, Map.of(TARGET_FIELD, target)))
          .isInstanceOf(IllegalArgumentException.class);
    }
    final Card hand =
        game.cards.stream()
            .filter(c -> c.owner == 0 && "hand".equals(Objects.requireNonNull(c.zone)))
            .findFirst()
            .orElseThrow();
    action(game, 0, "MOVE", Map.of(CARD_ID_FIELD, hand.id, "zone", "battlefield"));
    assertThatThrownBy(
            () -> action(game, 0, "CONTROL", Map.of(CARD_ID_FIELD, hand.id, TARGET_FIELD, target)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                action(
                    game, 0, "REVEAL", Map.of(CARD_ID_FIELD, hand.id, "audience", List.of(target))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> action(game, 0, "COMMANDER_CAST", Map.of("key", "invalid")))
        .hasMessageContaining("does not use commanders");
    assertThatThrownBy(
            () -> action(game, 0, "MOVE", Map.of(CARD_ID_FIELD, hand.id, "zone", "command")))
        .hasMessageContaining("commanders");
  }

  @Test
  void copyingOneIdentityAcrossDefinitionsStillCountsTowardFourCopies() {
    final Game game = standard();
    final List<Definition> deck = new ArrayList<>(Collections.nCopies(55, island()));
    final Definition firstDefinition =
        card("Same identity", CREATURE_TYPE, "", LEGAL_STATUS, System.currentTimeMillis());
    final Definition secondDefinition =
        new Definition(
            firstDefinition.id(),
            firstDefinition.name(),
            "https://cards.scryfall.io/other-printing.jpg",
            firstDefinition.text(),
            firstDefinition.type(),
            firstDefinition.colors(),
            firstDefinition.legal(),
            firstDefinition.faces(),
            firstDefinition.legalities(),
            firstDefinition.legalityCheckedAt());
    deck.addAll(Collections.nCopies(2, firstDefinition));
    deck.addAll(Collections.nCopies(3, secondDefinition));
    engine.load(game, 0, deck, List.of());
    assertThat(game.players.getFirst().warnings).anyMatch(w -> w.contains("four copies"));
  }

  @Test
  void aSingleReadyPlayerCannotStartAndMalformedFirstPlayersAreRejected() {
    final Game game = new Game();
    game.format = GameFormat.STANDARD;
    engine.join(game, "Only player");
    engine.load(game, 0, Collections.nCopies(60, island()), List.of());
    action(game, 0, READY_COMMAND, Map.of());
    assertThatThrownBy(() -> action(game, 0, START_COMMAND, Map.of()))
        .hasMessageContaining("Two ready");
    engine.join(game, "Second");
    engine.load(game, 1, Collections.nCopies(60, island()), List.of());
    action(game, 1, READY_COMMAND, Map.of());
    for (final Object first : List.of(-1, 2, 0.5, "1")) {
      assertThatThrownBy(() -> action(game, 0, START_COMMAND, Map.of(FIRST_PLAYER_KEY, first)))
          .hasMessageContaining(FIRST_PLAYER_KEY);
    }
  }
}
