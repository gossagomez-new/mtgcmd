package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;

import dev.mtgcmd.game.Game.Command;
import dev.mtgcmd.game.Game.Definition;
import dev.mtgcmd.game.Game.Player;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;

/** Public tabletop facade; RoomStore owns synchronization and durable transactions. */
public final class GameEngine {
  public static final List<String> ZONES = GameZones.ALL;
  public static final List<String> PHASES = LifecycleActions.PHASES;
  private final Clock clock;
  private final GameViewMapper views;
  private final Map<String, CommandHandler> handlers;

  public GameEngine() {
    this(Clock.systemUTC());
  }

  public GameEngine(final Clock clock) {
    this.clock = clock;
    final SecureRandom random = new SecureRandom();
    final GameAccess access = new GameAccess();
    final CardZoneTransitions transitions = new CardZoneTransitions(random);
    views = new GameViewMapper(access);
    final LifecycleActions lifecycleActions = new LifecycleActions(transitions);
    final LibraryActions libraryActions = new LibraryActions(access, transitions);
    final PermanentActions permanentActions = new PermanentActions(access, transitions);
    final ObjectActions objectActions = new ObjectActions(access);
    final CounterActions counterActions = new CounterActions(access, random);
    handlers =
        Map.ofEntries(
            Map.entry("READY", lifecycleActions::ready),
            Map.entry("START", lifecycleActions::start),
            Map.entry("PHASE", lifecycleActions::phase),
            Map.entry("END_TURN", lifecycleActions::endTurn),
            Map.entry("CONCEDE", lifecycleActions::concede),
            Map.entry("FINISH", lifecycleActions::finish),
            Map.entry("DRAW", libraryActions::draw),
            Map.entry("SHUFFLE", libraryActions::shuffle),
            Map.entry("SEARCH", libraryActions::search),
            Map.entry("CLOSE_SEARCH", libraryActions::closeSearch),
            Map.entry("GRANT", libraryActions::grant),
            Map.entry("MOVE", permanentActions::move),
            Map.entry("TAP", permanentActions::tap),
            Map.entry("UNTAP_ALL", permanentActions::untapAll),
            Map.entry("FACE_DOWN", permanentActions::faceDown),
            Map.entry("TRANSFORM", permanentActions::transform),
            Map.entry("REVEAL", permanentActions::reveal),
            Map.entry("CONTROL", permanentActions::control),
            Map.entry("ATTACH", permanentActions::attach),
            Map.entry("POSITION", permanentActions::position),
            Map.entry("COMBAT", permanentActions::combat),
            Map.entry("TOKEN", objectActions::token),
            Map.entry("EDIT_TOKEN", objectActions::editToken),
            Map.entry("COPY", objectActions::copy),
            Map.entry("ABILITY", objectActions::ability),
            Map.entry("RESOLVE_ABILITY", objectActions::resolveAbility),
            Map.entry("CARD_COUNTER", counterActions::cardCounter),
            Map.entry("COUNTER", counterActions::counter),
            Map.entry("COMMANDER_CAST", counterActions::commanderCast),
            Map.entry("DICE", counterActions::dice));
  }

  public void join(final Game game, final String name) {
    require("lobby".equals(Objects.requireNonNull(game.status)), "Game has already started");
    require(game.players.size() < game.format().seatCapacity(), "Room is full");
    final Player player = new Player();
    player.seat = game.players.size();
    player.name = clean(name, "name", 40);
    player.counters.put("life", game.format().startingLife());
    game.players.add(player);
    game.revision++;
    log(game, player.name + " joined seat " + (player.seat + 1));
  }

  public void load(
      final Game game, final int seat, final List<Definition> deck, final List<String> commanders) {
    require("lobby".equals(Objects.requireNonNull(game.status)), "Decks cannot change after start");
    final List<String> warnings = DeckRules.validate(game.format(), deck, commanders, clock);
    final Player player = player(game, seat);
    player.deck = new ArrayList<>(deck);
    player.commanders = commanders == null ? new ArrayList<>() : new ArrayList<>(commanders);
    player.warnings = warnings.stream().distinct().toList();
    player.ready = false;
    player.acceptedWarnings = false;
    game.revision++;
    player.deckRevision = game.revision;
    log(game, player.name + " imported a deck (" + deck.size() + " cards)");
  }

  public void apply(final Game game, final int seat, final Command command) {
    require(command != null && command.data() != null && command.type() != null, "Invalid command");
    require(command.revision() == game.revision, "Stale revision");
    final Player actor = player(game, seat);
    admit(game, actor, command.type());
    final CommandHandler handler = handlers.get(command.type());
    require(handler != null, "Unknown command");
    final String description = handler.execute(game, seat, command.data());
    game.revision++;
    log(game, actor.name + ": " + description);
  }

  private void admit(final Game game, final Player actor, final String type) {
    if ("READY".equals(type) || "START".equals(type)) {
      return;
    }
    require("playing".equals(Objects.requireNonNull(game.status)), "Game is not playing");
    require(!actor.conceded || "FINISH".equals(type), "You have conceded");
  }

  public Map<String, Object> view(final Game game, final int seat) {
    return views.forPlayer(game, seat);
  }

  public static String clean(final String value, final String name, final int limit) {
    return CommandInput.clean(value, name, limit);
  }

  private static void log(final Game game, final String text) {
    game.log.add(text);
    if (game.log.size() > 100) {
      game.log.removeFirst();
    }
  }
}
