package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.Game.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

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

  public GameEngine(Clock clock) {
    this.clock = clock;
    SecureRandom random = new SecureRandom();
    GameAccess access = new GameAccess();
    CardZoneTransitions transitions = new CardZoneTransitions(random);
    views = new GameViewMapper(access);
    LifecycleActions lifecycleActions = new LifecycleActions(transitions);
    LibraryActions libraryActions = new LibraryActions(access, transitions);
    PermanentActions permanentActions = new PermanentActions(access, transitions);
    ObjectActions objectActions = new ObjectActions(access);
    CounterActions counterActions = new CounterActions(access, random);
    handlers =
        Map.ofEntries(
            Map.entry("READY", lifecycleActions::ready),
            Map.entry("START", lifecycleActions::start),
            Map.entry("PHASE", lifecycleActions::phase),
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

  public void join(Game g, String name) {
    require(g.status.equals("lobby"), "Game has already started");
    require(g.players.size() < g.format().seatCapacity(), "Room is full");
    Player p = new Player();
    p.seat = g.players.size();
    p.name = clean(name, "name", 40);
    p.counters.put("life", g.format().startingLife());
    g.players.add(p);
    g.revision++;
    log(g, p.name + " joined seat " + (p.seat + 1));
  }

  public void load(Game g, int seat, List<Definition> deck, List<String> commanders) {
    require(g.status.equals("lobby"), "Decks cannot change after start");
    List<String> warnings = DeckRules.validate(g.format(), deck, commanders, clock);
    Player p = player(g, seat);
    p.deck = new ArrayList<>(deck);
    p.commanders = commanders == null ? new ArrayList<>() : new ArrayList<>(commanders);
    p.warnings = warnings.stream().distinct().toList();
    p.ready = false;
    p.acceptedWarnings = false;
    g.revision++;
    p.deckRevision = g.revision;
    log(g, p.name + " imported a deck (" + deck.size() + " cards)");
  }

  public void apply(Game g, int seat, Command command) {
    require(command != null && command.data() != null && command.type() != null, "Invalid command");
    require(command.revision() == g.revision, "Stale revision");
    Player actor = player(g, seat);
    admit(g, actor, command.type());
    CommandHandler handler = handlers.get(command.type());
    require(handler != null, "Unknown command");
    String description = handler.execute(g, seat, command.data());
    g.revision++;
    log(g, actor.name + ": " + description);
  }

  private void admit(Game game, Player actor, String type) {
    if (type.equals("READY") || type.equals("START")) return;
    require(game.status.equals("playing"), "Game is not playing");
    require(!actor.conceded || type.equals("FINISH"), "You have conceded");
  }

  public Map<String, Object> view(Game game, int seat) {
    return views.forPlayer(game, seat);
  }

  public static String clean(String value, String name, int limit) {
    return CommandInput.clean(value, name, limit);
  }

  private static void log(Game g, String text) {
    g.log.add(text);
    if (g.log.size() > 100) g.log.removeFirst();
  }
}
