package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.Game.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StandardGameTest {
  private final GameEngine engine = new GameEngine();

  private Game standard() {
    Game game = new Game();
    game.format = GameFormat.STANDARD;
    engine.join(game, "A");
    engine.join(game, "B");
    return game;
  }

  private Definition card(String id, String type, String text, String legality, Long checkedAt) {
    return new Definition(
        id,
        id,
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
    return card("Island", "Basic Land — Island", "", "legal", System.currentTimeMillis());
  }

  private void action(Game game, int seat, String type, Map<String, Object> data) {
    engine.apply(game, seat, new Command(UUID.randomUUID().toString(), game.revision, type, data));
  }

  private Game ready() {
    Game game = standard();
    for (int seat = 0; seat < 2; seat++) {
      engine.load(game, seat, Collections.nCopies(60, island()), List.of());
      action(game, seat, "READY", Map.of("acceptWarnings", true));
    }
    return game;
  }

  @Test
  void standardHasTwoTwentyLifeSeats() {
    Game game = standard();
    assertThat(game.players)
        .allSatisfy(player -> assertThat(player.counters.get("life")).isEqualTo(20));
    assertThatThrownBy(() -> engine.join(game, "C")).hasMessage("Room is full");
    assertThat(engine.view(game, 0))
        .containsEntry("format", "STANDARD")
        .containsEntry("seatCapacity", 2);
  }

  @ParameterizedTest
  @ValueSource(ints = {60, 61, 250})
  void acceptsDeckSizeBoundariesWithoutCommanders(int count) {
    Game game = standard();
    engine.load(game, 0, Collections.nCopies(count, island()), null);
    assertThat(game.players.getFirst().deck).hasSize(count);
    assertThat(game.players.getFirst().commanders).isEmpty();
    assertThat(game.players.getFirst().warnings).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 59, 251})
  void rejectsDeckSizeBoundaries(int count) {
    Game game = standard();
    assertThatThrownBy(() -> engine.load(game, 0, Collections.nCopies(count, island()), List.of()))
        .hasMessageContaining("60–250");
    assertThat(game.players.getFirst().deck).isEmpty();
  }

  @Test
  void rejectsCommandersAndWarnsAboutCopiesAndLegalityWithoutCommanderRules() {
    Game game = standard();
    assertThatThrownBy(
            () -> engine.load(game, 0, Collections.nCopies(60, island()), List.of("Island")))
        .hasMessageContaining("does not use commanders");
    var deck = new ArrayList<>(Collections.nCopies(55, island()));
    deck.addAll(
        Collections.nCopies(5, card("Spell", "Sorcery", "", "banned", System.currentTimeMillis())));
    engine.load(game, 0, deck, List.of());
    assertThat(game.players.getFirst().warnings)
        .anyMatch(w -> w.contains("not listed as Standard legal"))
        .anyMatch(w -> w.contains("four copies"))
        .noneMatch(
            w ->
                w.contains("Commander") || w.contains("singleton") || w.contains("color identity"));
    assertThatThrownBy(() -> action(game, 0, "READY", Map.of()))
        .hasMessageContaining("Accept deck warnings");
    action(game, 0, "READY", Map.of("acceptWarnings", true));
    assertThat(game.players.getFirst().ready).isTrue();
  }

  @Test
  void anyNumberExceptionAndFourCopiesAreAllowed() {
    Game game = standard();
    var deck = new ArrayList<>(Collections.nCopies(52, island()));
    deck.addAll(
        Collections.nCopies(
            4, card("Normal", "Creature", "", "legal", System.currentTimeMillis())));
    deck.addAll(
        Collections.nCopies(
            4,
            card(
                "Exception",
                "Creature",
                "A deck can have any number of cards named Exception.",
                "legal",
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
                "Creature",
                "A deck can have any number of cards named Exception.",
                "legal",
                System.currentTimeMillis())),
        List.of());
    assertThat(game.players.getFirst().warnings).isEmpty();
  }

  @Test
  void missingStaleAndFutureLegalityRequireVerificationWarnings() {
    for (Long time :
        Arrays.asList(
            null,
            System.currentTimeMillis() - 86_400_001,
            System.currentTimeMillis() + 86_400_000)) {
      Game game = standard();
      engine.load(
          game,
          0,
          Collections.nCopies(60, card("Island", "Basic Land", "", "legal", time)),
          List.of());
      assertThat(game.players.getFirst().warnings)
          .anyMatch(w -> w.contains("could not be verified"));
    }
  }

  @Test
  void hostChoosesStartingPlayerAndTurnsAlternateWithSevenCards() {
    Game game = ready();
    assertThatThrownBy(() -> action(game, 1, "START", Map.of())).hasMessageContaining("host");
    assertThatThrownBy(() -> action(game, 0, "START", Map.of("firstPlayer", 2)))
        .hasMessageContaining("firstPlayer");
    action(game, 0, "START", Map.of("firstPlayer", 1));
    assertThat(game.active).isEqualTo(1);
    assertThat(game.cards).noneMatch(c -> c.zone.equals("command"));
    for (int seat = 0; seat < 2; seat++) {
      final int owner = seat;
      assertThat(game.cards.stream().filter(c -> c.owner == owner && c.zone.equals("hand")))
          .hasSize(7);
      assertThat(game.cards.stream().filter(c -> c.owner == owner && c.zone.equals("library")))
          .hasSize(53);
    }
    game.phase = "Cleanup";
    action(game, 1, "PHASE", Map.of());
    assertThat(game.active).isZero();
    game.phase = "Cleanup";
    action(game, 0, "PHASE", Map.of());
    assertThat(game.active).isEqualTo(1);
    assertThatThrownBy(() -> action(game, 0, "GRANT", Map.of("target", 2)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> action(game, 0, "DRAW", Map.of("target", 1)))
        .hasMessageContaining("permission");
    action(game, 1, "CONCEDE", Map.of());
    assertThat(game.active).isZero();
  }

  @Test
  void twoReadySeatsAreMandatoryAndOmittedFirstPlayerDefaultsToHost() {
    Game game = standard();
    assertThatThrownBy(() -> action(game, 0, "START", Map.of())).hasMessageContaining("ready");
    Game started = ready();
    action(started, 0, "START", Map.of());
    assertThat(started.active).isZero();
  }

  @Test
  void legacyJsonDefaultsToCommanderAndPreservesCountersAndDefinitions() throws Exception {
    var mapper = new ObjectMapper();
    Game game =
        mapper.readValue(
            "{\"status\":\"playing\",\"players\":[{\"seat\":0,\"counters\":{\"life\":17}}]}",
            Game.class);
    assertThat(game.format()).isEqualTo(GameFormat.COMMANDER);
    assertThat(game.players.getFirst().counters.get("life")).isEqualTo(17);
    Definition def =
        mapper.readValue(
            "{\"id\":\"island\",\"name\":\"Island\",\"image\":\"\",\"text\":\"\",\"type\":\"Basic Land\",\"colors\":[],\"legal\":true,\"faces\":[]}",
            Definition.class);
    assertThat(def.legal()).isTrue();
    assertThat(def.legalities()).isEmpty();
    assertThat(def.legalityCheckedAt()).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 2, 3})
  void invalidTargetsCannotGrantSearchDrawControlOrReveal(int target) {
    Game game = ready();
    action(game, 0, "START", Map.of());
    for (String type : List.of("DRAW", "SEARCH", "GRANT")) {
      assertThatThrownBy(() -> action(game, 0, type, Map.of("target", target)))
          .isInstanceOf(IllegalArgumentException.class);
    }
    Card hand =
        game.cards.stream()
            .filter(c -> c.owner == 0 && c.zone.equals("hand"))
            .findFirst()
            .orElseThrow();
    action(game, 0, "MOVE", Map.of("cardId", hand.id, "zone", "battlefield"));
    assertThatThrownBy(
            () -> action(game, 0, "CONTROL", Map.of("cardId", hand.id, "target", target)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> action(game, 0, "REVEAL", Map.of("cardId", hand.id, "audience", List.of(target))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> action(game, 0, "COMMANDER_CAST", Map.of("key", "invalid")))
        .hasMessageContaining("does not use commanders");
    assertThatThrownBy(() -> action(game, 0, "MOVE", Map.of("cardId", hand.id, "zone", "command")))
        .hasMessageContaining("commanders");
  }

  @Test
  void copyingOneIdentityAcrossDefinitionsStillCountsTowardFourCopies() {
    Game game = standard();
    var deck = new ArrayList<>(Collections.nCopies(55, island()));
    Definition a = card("Same identity", "Creature", "", "legal", System.currentTimeMillis());
    Definition b =
        new Definition(
            a.id(),
            a.name(),
            "https://cards.scryfall.io/other-printing.jpg",
            a.text(),
            a.type(),
            a.colors(),
            a.legal(),
            a.faces(),
            a.legalities(),
            a.legalityCheckedAt());
    deck.addAll(Collections.nCopies(2, a));
    deck.addAll(Collections.nCopies(3, b));
    engine.load(game, 0, deck, List.of());
    assertThat(game.players.getFirst().warnings).anyMatch(w -> w.contains("four copies"));
  }

  @Test
  void aSingleReadyPlayerCannotStartAndMalformedFirstPlayersAreRejected() {
    Game game = new Game();
    game.format = GameFormat.STANDARD;
    engine.join(game, "Only player");
    engine.load(game, 0, Collections.nCopies(60, island()), List.of());
    action(game, 0, "READY", Map.of());
    assertThatThrownBy(() -> action(game, 0, "START", Map.of())).hasMessageContaining("Two ready");
    engine.join(game, "Second");
    engine.load(game, 1, Collections.nCopies(60, island()), List.of());
    action(game, 1, "READY", Map.of());
    for (Object first : List.of(-1, 2, 0.5, "1")) {
      assertThatThrownBy(() -> action(game, 0, "START", Map.of("firstPlayer", first)))
          .hasMessageContaining("firstPlayer");
    }
  }
}
