package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NullStateInvariantTest {
  private static final String COMMAND_ID = "command";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "classification",
        "visibility",
        "definition",
        "editing",
        "editable",
        "battlefield"
      })
  void missingZoneNeverBecomesPublicOrEditable(final String operation) {
    final Game game = new Game();
    final Game.Player player = new Game.Player();
    game.players.add(player);
    final Game.Card card = new Game.Card();
    card.id = "card";
    game.cards.add(card);
    final GameAccess access = new GameAccess();
    assertThatNullPointerException()
        .isThrownBy(
            () -> {
              switch (operation) {
                case "classification" -> GameZones.isPrivate(card);
                case "visibility" -> access.visible(game, 0, card);
                case "definition" -> access.canSeeDefinition(game, 0, card);
                case "editing" -> access.mayEdit(game, 0, card);
                case "editable" -> access.editable(game, 0, Map.of("cardId", "card"));
                case "battlefield" -> GameLookup.battlefield(card);
                default -> throw new AssertionError(operation);
              }
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"join", "load", "command", "ready", "start"})
  void missingStatusRemainsInvalid(final String operation) {
    final Game game = new Game();
    game.status = null;
    game.players.add(new Game.Player());
    final GameEngine engine = new GameEngine();
    assertThatNullPointerException()
        .isThrownBy(
            () -> {
              switch (operation) {
                case "join" -> engine.join(game, "player");
                case "load" -> engine.load(game, 0, List.of(), List.of());
                case "command" ->
                    engine.apply(
                        game, 0, new Game.Command(COMMAND_ID, 0, "DRAW", Map.of("count", 1)));
                case "ready" ->
                    engine.apply(game, 0, new Game.Command(COMMAND_ID, 0, "READY", Map.of()));
                case "start" ->
                    engine.apply(game, 0, new Game.Command(COMMAND_ID, 0, "START", Map.of()));
                default -> throw new AssertionError(operation);
              }
            });
    assertThat(game.revision).isZero();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void transitionsRejectMissingCurrentOrDestinationZone(final boolean nullDestination) {
    final Game game = new Game();
    final Game.Card card = new Game.Card();
    card.zone = nullDestination ? "battlefield" : null;
    game.cards.add(card);
    final CardZoneTransitions transitions = new CardZoneTransitions(new SecureRandom());
    assertThatNullPointerException()
        .isThrownBy(
            () -> transitions.move(game, card, nullDestination ? null : "hand", false, false));
    assertThat(game.revision).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"lobby", "playing"})
  void missingCommandTypeKeepsItsValidationError(final String status) {
    final Game game = new Game();
    game.status = status;
    game.players.add(new Game.Player());
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new GameEngine().apply(game, 0, new Game.Command(COMMAND_ID, 0, null, Map.of())))
        .withMessage("Invalid command");
    assertThat(game.revision).isZero();
  }

  @Test
  void untappingAllRejectsACardWithMissingZone() {
    final Game game = new Game();
    game.status = "playing";
    game.players.add(new Game.Player());
    final Game.Card card = new Game.Card();
    card.tapped = true;
    game.cards.add(card);
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new GameEngine()
                    .apply(game, 0, new Game.Command(COMMAND_ID, 0, "UNTAP_ALL", Map.of())));
    assertThat(card.tapped).isTrue();
    assertThat(game.revision).isZero();
  }

  @Test
  void libraryInsertionRejectsAnExistingCardWithMissingZone() {
    final Game game = new Game();
    game.cards.add(new Game.Card());
    final Game.Card moving = new Game.Card();
    moving.zone = "battlefield";
    game.cards.add(moving);
    final CardZoneTransitions transitions = new CardZoneTransitions(new SecureRandom());
    assertThatNullPointerException()
        .isThrownBy(() -> transitions.move(game, moving, "library", true, false));
    assertThat(game.revision).isZero();
  }
}
