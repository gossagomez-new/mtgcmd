package dev.mtgcmd.game;

import static dev.mtgcmd.game.Game.*;

import java.security.SecureRandom;
import java.util.*;

/** Physical tabletop actions, independent of card-rules interpretation. */
public final class GameEngine {
  public static final List<String> ZONES =
      List.of("library", "hand", "battlefield", "graveyard", "exile", "command", "stack");
  public static final List<String> PHASES =
      List.of(
          "Untap",
          "Upkeep",
          "Draw",
          "Main 1",
          "Begin combat",
          "Attackers",
          "Blockers",
          "Damage",
          "End combat",
          "Main 2",
          "End step",
          "Cleanup");
  private final SecureRandom random = new SecureRandom();

  public void join(Game g, String name) {
    require(g.status.equals("lobby"), "Game has already started");
    require(g.players.size() < 4, "Room is full");
    Player p = new Player();
    p.seat = g.players.size();
    p.name = clean(name, "name", 40);
    g.players.add(p);
    g.revision++;
    log(g, p.name + " joined seat " + (p.seat + 1));
  }

  public void load(Game g, int seat, List<Definition> deck, List<String> commanders) {
    require(g.status.equals("lobby"), "Decks cannot change after start");
    require(deck != null && !deck.isEmpty() && deck.size() <= 250, "Deck must contain 1–250 cards");
    require(
        commanders != null && !commanders.isEmpty() && commanders.size() <= 2,
        "Choose one or two commanders");
    require(new HashSet<>(commanders).size() == commanders.size(), "Commanders must be distinct");
    for (String id : commanders)
      require(deck.stream().anyMatch(d -> d.id().equals(id)), "Commander must be in the deck");
    Player p = player(g, seat);
    List<String> warnings = new ArrayList<>();
    if (deck.size() != 100)
      warnings.add(
          "Deck contains "
              + deck.size()
              + " cards; Commander normally uses 100 including commanders.");
    Set<String> identity = new HashSet<>();
    for (String id : commanders) {
      Definition def = deck.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
      identity.addAll(def.colors());
      if (!def.type().contains("Legendary") || !def.type().contains("Creature"))
        warnings.add("Check commander eligibility for " + def.name() + ".");
    }
    if (commanders.size() == 2) warnings.add("Check that the chosen commanders can be paired.");
    Set<String> seen = new HashSet<>();
    for (Definition def : deck) {
      if (!def.legal()) warnings.add(def.name() + " is not listed as Commander legal.");
      if (!identity.containsAll(def.colors()))
        warnings.add(def.name() + " exceeds the chosen color identity.");
      if (!seen.add(def.id())
          && !def.type().contains("Basic")
          && !def.text().contains("any number of cards named"))
        warnings.add("Check singleton exception for " + def.name() + ".");
    }
    p.deck = new ArrayList<>(deck);
    p.commanders = new ArrayList<>(commanders);
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
    Player p = player(g, seat);
    Map<String, Object> d = command.data();
    String description = command.type().toLowerCase(Locale.ROOT).replace('_', ' ');
    if (command.type().equals("READY")) {
      require(g.status.equals("lobby"), "Game has already started");
      boolean ready = bool(d, "ready", true), accepted = bool(d, "acceptWarnings", false);
      require(!ready || !p.deck.isEmpty(), "Import a deck first");
      require(
          !ready || p.warnings.isEmpty() || accepted, "Accept deck warnings before becoming ready");
      p.ready = ready;
      p.acceptedWarnings = accepted;
    } else if (command.type().equals("START")) {
      require(seat == g.host, "Only the host can start");
      require(g.status.equals("lobby"), "Game has already started");
      require(
          g.players.size() == 4 && g.players.stream().allMatch(x -> x.ready),
          "Four ready players are required");
      start(g);
    } else {
      require(g.status.equals("playing"), "Game is not playing");
      require(!p.conceded || command.type().equals("FINISH"), "You have conceded");
      switch (command.type()) {
        case "DRAW" -> {
          int target = integer(d, "target", seat, 0, 3), count = integer(d, "count", 1, 1, 100);
          privatePermission(g, seat, target);
          List<Card> library = zone(g, target, "library");
          require(library.size() >= count, "Not enough cards in library");
          for (Card c : library.subList(0, count)) move(g, c, "hand", false, false);
          description = "drew " + count + " card(s)";
        }
        case "SHUFFLE" -> {
          int target = integer(d, "target", seat, 0, 3);
          privatePermission(g, seat, target);
          shuffle(g, target);
          description = "shuffled a library";
        }
        case "SEARCH" -> {
          int target = integer(d, "target", seat, 0, 3);
          privatePermission(g, seat, target);
          g.searches.put(seat, target);
          description = "opened a private library search";
        }
        case "CLOSE_SEARCH" -> g.searches.remove(seat);
        case "GRANT" -> {
          int target = integer(d, "target", -1, 0, 3);
          player(g, target);
          Set<Integer> grants = g.grants.computeIfAbsent(seat, x -> new HashSet<>());
          if (bool(d, "allow", true)) grants.add(target);
          else {
            grants.remove(target);
            if (Objects.equals(g.searches.get(target), seat)) g.searches.remove(target);
          }
        }
        case "MOVE" -> {
          Card c = editable(g, seat, d);
          String destination = string(d, "zone", 20);
          require(ZONES.contains(destination), "Unknown zone");
          if (destination.equals("command"))
            require(c.commanderKey != null, "Only designated commanders enter the command zone");
          move(g, c, destination, bool(d, "top", false), bool(d, "faceDown", false));
          c.stackOrder = g.revision + 1;
        }
        case "TAP" -> {
          Card c = editable(g, seat, d);
          battlefield(c);
          c.tapped = bool(d, "tapped", !c.tapped);
        }
        case "UNTAP_ALL" ->
            g.cards.stream()
                .filter(c -> c.zone.equals("battlefield") && c.controller == seat)
                .forEach(c -> c.tapped = false);
        case "FACE_DOWN" -> {
          Card c = editable(g, seat, d);
          require(!isPrivate(c), "Face-down applies to public objects");
          c.faceDown = bool(d, "faceDown", !c.faceDown);
          c.revealedTo.clear();
        }
        case "TRANSFORM" -> {
          Card c = editable(g, seat, d);
          require(c.definition.faces().size() > 1, "Card has no other face");
          c.face = (c.face + 1) % c.definition.faces().size();
        }
        case "REVEAL" -> {
          Card c = editable(g, seat, d);
          Object audience = d.get("audience");
          require(audience instanceof List<?>, "Reveal requires an audience");
          Set<Integer> seats = new HashSet<>();
          for (Object value : (List<?>) audience) {
            require(value instanceof Number, "Invalid audience");
            int viewer = exactInteger((Number) value, "audience", 0, 3);
            player(g, viewer);
            seats.add(viewer);
          }
          c.revealedTo = seats;
        }
        case "CONTROL" -> {
          Card c = editable(g, seat, d);
          battlefield(c);
          int target = integer(d, "target", -1, 0, 3);
          require(!player(g, target).conceded, "Target player conceded");
          c.controller = target;
          c.zoneOwner = target;
          c.combat = "";
        }
        case "ATTACH" -> {
          Card c = editable(g, seat, d);
          battlefield(c);
          String targetId = optionalString(d, "targetId", 80);
          if (targetId.isEmpty()) c.attachedTo = null;
          else {
            Card target = find(g, targetId);
            battlefield(target);
            Set<String> visited = new HashSet<>(Set.of(c.id));
            Card cursor = target;
            while (cursor != null) {
              require(visited.add(cursor.id), "Attachment cycle");
              cursor = cursor.attachedTo == null ? null : find(g, cursor.attachedTo);
            }
            c.attachedTo = target.id;
          }
        }
        case "CARD_COUNTER" -> updateCounter(editable(g, seat, d).counters, d);
        case "COUNTER" -> updateCounter(p.counters, d);
        case "COMMANDER_CAST" -> {
          String key = string(d, "key", 80);
          require(
              g.cards.stream().anyMatch(c -> key.equals(c.commanderKey) && c.owner == seat),
              "Unknown commander");
          updateCounter(
              p.counters,
              Map.of("name", "casts:" + key, "delta", integer(d, "delta", 1, -100, 100)));
        }
        case "TOKEN" -> {
          String name = string(d, "name", 100),
              text = optionalString(d, "text", 2000),
              type = optionalString(d, "type", 200);
          require(g.cards.size() < 2000, "Table has too many objects");
          Card c = new Card();
          c.definition =
              new Definition(
                  UUID.randomUUID().toString(), name, "", text, type, List.of(), true, List.of());
          c.owner = seat;
          c.controller = seat;
          c.zoneOwner = seat;
          c.zone = "battlefield";
          c.token = true;
          g.cards.add(c);
        }
        case "EDIT_TOKEN" -> {
          Card c = editable(g, seat, d);
          require(c.token, "Only token characteristics can be edited");
          c.definition =
              new Definition(
                  c.definition.id(),
                  string(d, "name", 100),
                  c.definition.image(),
                  optionalString(d, "text", 2000),
                  optionalString(d, "type", 200),
                  List.of(),
                  true,
                  List.of());
        }
        case "COPY" -> {
          Card source = find(g, string(d, "cardId", 80));
          require(canSeeDefinition(g, seat, source), "No permission to inspect this card");
          require(!isPrivate(source), "Copy a public card");
          require(g.cards.size() < 2000, "Table has too many objects");
          Card c = new Card();
          c.definition = source.definition;
          c.face = source.face;
          c.owner = seat;
          c.controller = seat;
          c.zoneOwner = seat;
          c.zone = "battlefield";
          c.token = true;
          g.cards.add(c);
        }
        case "POSITION" -> {
          Card c = editable(g, seat, d);
          battlefield(c);
          double x = decimal(d, "x"), y = decimal(d, "y");
          c.x = x;
          c.y = y;
        }
        case "COMBAT" -> {
          Card c = editable(g, seat, d);
          battlefield(c);
          String marker = optionalString(d, "marker", 80);
          require(
              marker.isEmpty() || marker.equals("attacker") || marker.startsWith("blocks:"),
              "Invalid combat marker");
          if (marker.startsWith("blocks:")) battlefield(find(g, marker.substring(7)));
          c.combat = marker;
        }
        case "ABILITY" -> {
          require(g.abilities.size() < 200, "Stack is full");
          Ability a = new Ability();
          a.controller = seat;
          a.text = string(d, "text", 500);
          a.stackOrder = g.revision + 1;
          g.abilities.add(a);
        }
        case "RESOLVE_ABILITY" -> {
          String id = string(d, "abilityId", 80);
          Ability a =
              g.abilities.stream()
                  .filter(x -> x.id.equals(id))
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("Unknown ability"));
          require(a.controller == seat, "No permission to resolve this ability");
          g.abilities.remove(a);
        }
        case "PHASE" -> {
          require(
              seat == g.active || seat == g.host, "Only the active player or host advances phases");
          int index = PHASES.indexOf(g.phase);
          if (index == PHASES.size() - 1) {
            for (int i = 0; i < 4; i++) {
              g.active = (g.active + 1) % 4;
              if (!g.players.get(g.active).conceded) break;
            }
            g.turn++;
            g.phase = PHASES.getFirst();
            g.cards.forEach(c -> c.combat = "");
          } else g.phase = PHASES.get(index + 1);
          description = "advanced to " + g.phase;
        }
        case "DICE" ->
            description = "rolled " + (random.nextInt(integer(d, "sides", 6, 2, 1000)) + 1);
        case "CONCEDE" -> {
          p.conceded = true;
          g.grants.remove(seat);
          g.grants.values().forEach(s -> s.remove(seat));
          g.searches.remove(seat);
          if (g.active == seat && g.players.stream().anyMatch(other -> !other.conceded)) {
            do {
              g.active = (g.active + 1) % g.players.size();
            } while (g.players.get(g.active).conceded);
            g.turn++;
            g.phase = PHASES.getFirst();
            g.cards.forEach(c -> c.combat = "");
          }
        }
        case "FINISH" -> {
          require(seat == g.host, "Only the host can finish");
          g.status = "finished";
        }
        default -> throw new IllegalArgumentException("Unknown command");
      }
    }
    g.revision++;
    log(g, p.name + ": " + description);
  }

  private void start(Game g) {
    for (Player p : g.players) {
      Set<String> remaining = new HashSet<>(p.commanders);
      for (Definition def : p.deck) {
        Card c = new Card();
        c.definition = def;
        c.owner = p.seat;
        c.controller = p.seat;
        c.zoneOwner = p.seat;
        boolean commander = remaining.remove(def.id());
        c.zone = commander ? "command" : "library";
        c.commanderKey = commander ? UUID.randomUUID().toString() : null;
        g.cards.add(c);
      }
      shuffle(g, p.seat);
      for (Card c : zone(g, p.seat, "library").stream().limit(7).toList())
        move(g, c, "hand", false, false);
    }
    g.status = "playing";
  }

  private void shuffle(Game g, int owner) {
    List<Card> library = zone(g, owner, "library");
    Collections.shuffle(library, random);
    g.cards.removeAll(library);
    for (Card c : library) {
      rekey(g, c);
      c.revealedTo.clear();
    }
    g.cards.addAll(library);
    g.searches.entrySet().removeIf(e -> e.getValue() == owner);
  }

  private void move(Game g, Card c, String destination, boolean top, boolean faceDown) {
    String previous = c.zone, oldId = c.id;
    g.cards.forEach(
        other -> {
          if (oldId.equals(other.attachedTo)) other.attachedTo = null;
          if (other.combat.equals("blocks:" + oldId)) other.combat = "";
        });
    c.attachedTo = null;
    c.combat = "";
    c.counters.clear();
    c.tapped = false;
    c.x = 0;
    c.y = 0;
    c.face = 0;
    c.revealedTo.clear();
    c.zone = destination;
    c.faceDown = faceDown;
    if (!destination.equals("battlefield") && !destination.equals("stack")) c.controller = c.owner;
    c.zoneOwner =
        destination.equals("battlefield") || destination.equals("stack") ? c.controller : c.owner;
    if (Set.of("library", "hand").contains(previous) || isPrivate(c)) rekey(g, c);
    g.cards.remove(c);
    if (c.token && !destination.equals("battlefield") && !destination.equals("stack")) return;
    if (top && destination.equals("library")) {
      int index = 0;
      while (index < g.cards.size()
          && !(g.cards.get(index).zone.equals("library")
              && g.cards.get(index).zoneOwner == c.zoneOwner)) index++;
      g.cards.add(index, c);
    } else g.cards.add(c);
  }

  private void rekey(Game g, Card c) {
    String old = c.id;
    c.id = UUID.randomUUID().toString();
    g.cards.forEach(
        other -> {
          if (old.equals(other.attachedTo)) other.attachedTo = null;
        });
  }

  public Map<String, Object> view(Game g, int seat) {
    player(g, seat);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", g.id);
    out.put("revision", g.revision);
    out.put("status", g.status);
    out.put("host", g.host);
    out.put("active", g.active);
    out.put("turn", g.turn);
    out.put("phase", g.phase);
    out.put("you", seat);
    out.put("grants", g.grants.getOrDefault(seat, Set.of()));
    out.put("search", g.searches.get(seat));
    out.put("log", g.log);
    out.put(
        "players",
        g.players.stream()
            .map(
                p -> {
                  Map<String, Object> v = new LinkedHashMap<>();
                  v.put("seat", p.seat);
                  v.put("name", p.name);
                  v.put("ready", p.ready);
                  v.put("conceded", p.conceded);
                  v.put("deckCount", p.deck.size());
                  v.put("deckRevision", p.deckRevision);
                  v.put("privateAccess", canAccessPrivate(g, seat, p.seat));
                  v.put("warnings", p.seat == seat ? p.warnings : List.of());
                  v.put("counters", p.counters);
                  Map<String, Long> counts = new LinkedHashMap<>();
                  for (String z : ZONES) counts.put(z, (long) zone(g, p.seat, z).size());
                  v.put("counts", counts);
                  v.put(
                      "commanders",
                      g.cards.stream()
                          .filter(c -> c.owner == p.seat && c.commanderKey != null)
                          .map(
                              c ->
                                  Map.of(
                                      "key",
                                      c.commanderKey,
                                      "name",
                                      c.definition.name(),
                                      "casts",
                                      p.counters.getOrDefault("casts:" + c.commanderKey, 0)))
                          .toList());
                  return v;
                })
            .toList());
    out.put(
        "cards",
        g.cards.stream()
            .filter(c -> visible(g, seat, c))
            .map(
                c -> {
                  Map<String, Object> v = new LinkedHashMap<>();
                  v.put("id", c.id);
                  v.put("owner", c.owner);
                  v.put("controller", c.controller);
                  v.put("zoneOwner", c.zoneOwner);
                  v.put("zone", c.zone);
                  v.put("tapped", c.tapped);
                  v.put("faceDown", c.faceDown);
                  v.put("face", c.face);
                  v.put("token", c.token);
                  v.put("commanderKey", canSeeDefinition(g, seat, c) ? c.commanderKey : null);
                  v.put("attachedTo", c.attachedTo);
                  v.put("combat", c.combat);
                  v.put("stackOrder", c.stackOrder);
                  v.put("x", c.x);
                  v.put("y", c.y);
                  v.put("counters", c.counters);
                  v.put("editable", mayEdit(g, seat, c));
                  if (canSeeDefinition(g, seat, c)) v.put("definition", c.definition);
                  return v;
                })
            .toList());
    out.put("abilities", g.abilities);
    return out;
  }

  private boolean visible(Game g, int seat, Card c) {
    if (!isPrivate(c)) return true;
    if (c.revealedTo.contains(seat)) return true;
    if (!canAccessPrivate(g, seat, c.zoneOwner)) return false;
    return !c.zone.equals("library") || Objects.equals(g.searches.get(seat), c.zoneOwner);
  }

  private boolean canSeeDefinition(Game g, int seat, Card c) {
    return visible(g, seat, c)
        && (!c.faceDown || c.controller == seat || c.revealedTo.contains(seat));
  }

  private boolean mayEdit(Game g, int seat, Card c) {
    if (player(g, seat).conceded) return false;
    if (isPrivate(c))
      return canAccessPrivate(g, seat, c.zoneOwner)
          && (!c.zone.equals("library") || Objects.equals(g.searches.get(seat), c.zoneOwner));
    return c.controller == seat;
  }

  private Card editable(Game g, int seat, Map<String, Object> d) {
    Card c = find(g, string(d, "cardId", 80));
    if (c.zone.equals("library") && canAccessPrivate(g, seat, c.zoneOwner))
      require(Objects.equals(g.searches.get(seat), c.zoneOwner), "Open a library search first");
    require(mayEdit(g, seat, c), "No permission to change this card");
    return c;
  }

  private boolean canAccessPrivate(Game g, int seat, int owner) {
    return seat == owner || g.grants.getOrDefault(owner, Set.of()).contains(seat);
  }

  private void privatePermission(Game g, int seat, int owner) {
    player(g, owner);
    require(canAccessPrivate(g, seat, owner), "No permission for this private zone");
  }

  private static boolean isPrivate(Card c) {
    return c.zone.equals("library") || c.zone.equals("hand");
  }

  private static void battlefield(Card c) {
    require(c.zone.equals("battlefield"), "Card must be on the battlefield");
  }

  private static Card find(Game g, String id) {
    return g.cards.stream()
        .filter(c -> c.id.equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Card unavailable"));
  }

  private static List<Card> zone(Game g, int owner, String zone) {
    return g.cards.stream()
        .filter(c -> c.zoneOwner == owner && c.zone.equals(zone))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  private static Player player(Game g, int seat) {
    require(seat >= 0 && seat < g.players.size(), "Unknown seat");
    return g.players.get(seat);
  }

  private static void log(Game g, String text) {
    g.log.add(text);
    if (g.log.size() > 100) g.log.removeFirst();
  }

  private static void updateCounter(Map<String, Integer> counters, Map<String, Object> d) {
    String name = string(d, "name", 100);
    require(counters.containsKey(name) || counters.size() < 100, "Too many counters");
    int value =
        d.containsKey("value")
            ? integer(d, "value", 0, -99999, 99999)
            : counters.getOrDefault(name, 0) + integer(d, "delta", 0, -9999, 9999);
    require(value >= -99999 && value <= 99999, "Counter out of range");
    counters.put(name, value);
  }

  public static String clean(String value, String name, int limit) {
    require(value != null && !value.isBlank() && value.length() <= limit, "Invalid " + name);
    return value.trim();
  }

  private static String string(Map<String, Object> d, String key, int limit) {
    require(d.get(key) instanceof String, "Invalid " + key);
    return clean((String) d.get(key), key, limit);
  }

  private static String optionalString(Map<String, Object> d, String key, int limit) {
    if (!d.containsKey(key)) return "";
    require(
        d.get(key) instanceof String && ((String) d.get(key)).length() <= limit, "Invalid " + key);
    return ((String) d.get(key)).trim();
  }

  private static boolean bool(Map<String, Object> d, String key, boolean fallback) {
    if (!d.containsKey(key)) return fallback;
    require(d.get(key) instanceof Boolean, "Invalid " + key);
    return (Boolean) d.get(key);
  }

  private static int integer(Map<String, Object> d, String key, int fallback, int min, int max) {
    if (!d.containsKey(key)) {
      require(fallback >= min && fallback <= max, "Invalid " + key);
      return fallback;
    }
    require(d.get(key) instanceof Number, "Invalid " + key);
    return exactInteger((Number) d.get(key), key, min, max);
  }

  private static int exactInteger(Number n, String key, int min, int max) {
    double v = n.doubleValue();
    require(Double.isFinite(v) && v == Math.rint(v) && v >= min && v <= max, "Invalid " + key);
    return (int) v;
  }

  private static double decimal(Map<String, Object> d, String key) {
    require(d.get(key) instanceof Number, "Invalid " + key);
    double v = ((Number) d.get(key)).doubleValue();
    require(Double.isFinite(v) && v >= 0 && v <= 1, "Invalid " + key);
    return v;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}
