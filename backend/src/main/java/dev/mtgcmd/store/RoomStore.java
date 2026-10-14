package dev.mtgcmd.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class RoomStore {
  public static class Conflict extends IllegalArgumentException {
    public Conflict() {
      super("Stale revision");
    }
  }

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final ObjectMapper mapper;
  private final GameEngine engine;

  public RoomStore(
      JdbcTemplate jdbc, TransactionTemplate transactions, ObjectMapper mapper, GameEngine engine) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.mapper =
        mapper
            .copy()
            .configure(
                com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,
                true);
    this.engine = engine;
  }

  public Map<String, Object> create(String guest, String name) {
    return create(guest, name, GameFormat.COMMANDER);
  }

  public Map<String, Object> create(String guest, String name, GameFormat format) {
    return transactions.execute(
        ignored -> {
          Game g = new Game();
          g.format = format == null ? GameFormat.COMMANDER : format;
          engine.join(g, name);
          jdbc.update("INSERT INTO rooms(id,snapshot) VALUES (?,CAST(? AS jsonb))", g.id, json(g));
          jdbc.update("INSERT INTO members(room_id,guest_hash,seat) VALUES (?,?,0)", g.id, guest);
          event(g, 0);
          return engine.view(g, 0);
        });
  }

  public Map<String, Object> join(String guest, String id, String name) {
    return transactions.execute(
        ignored -> {
          Game g = read(id, true);
          List<Integer> seats =
              jdbc.queryForList(
                  "SELECT seat FROM members WHERE room_id=? AND guest_hash=?",
                  Integer.class,
                  id,
                  guest);
          if (!seats.isEmpty()) return engine.view(g, seats.getFirst());
          engine.join(g, name);
          int seat = g.players.size() - 1;
          jdbc.update(
              "INSERT INTO members(room_id,guest_hash,seat) VALUES (?,?,?)", id, guest, seat);
          save(g);
          event(g, seat);
          return engine.view(g, seat);
        });
  }

  public GameFormat format(String guest, String id) {
    seat(guest, id);
    return read(id, false).format();
  }

  public Map<String, Object> view(String guest, String id) {
    int seat = seat(guest, id);
    return engine.view(read(id, false), seat);
  }

  public int seat(String guest, String id) {
    List<Integer> seats =
        jdbc.queryForList(
            "SELECT seat FROM members WHERE room_id=? AND guest_hash=?", Integer.class, id, guest);
    if (seats.isEmpty()) throw new Forbidden("You are not a room member");
    return seats.getFirst();
  }

  public Map<String, Object> execute(String guest, String id, Game.Command command) {
    if (command == null || command.type() == null || command.data() == null)
      throw new IllegalArgumentException("Invalid command");
    return mutate(
        guest,
        id,
        command.id(),
        command.revision(),
        json(command),
        (g, s) -> engine.apply(g, s, command));
  }

  public Map<String, Object> load(
      String guest,
      String id,
      String commandId,
      long revision,
      List<Game.Definition> cards,
      List<String> commanders) {
    return mutate(
        guest,
        id,
        commandId,
        revision,
        json(Map.of("cards", cards, "commanders", commanders == null ? List.of() : commanders)),
        (g, s) -> engine.load(g, s, cards, commanders));
  }

  private Map<String, Object> mutate(
      String guest,
      String id,
      String commandId,
      long revision,
      String payload,
      BiConsumer<Game, Integer> action) {
    GameEngine.clean(commandId, "command ID", 80);
    return transactions.execute(
        ignored -> {
          Game g = read(id, true);
          int seat = seat(guest, id);
          String digest = hash(payload);
          List<String> previous =
              jdbc.queryForList(
                  "SELECT digest FROM commands WHERE room_id=? AND seat=? AND command_id=?",
                  String.class,
                  id,
                  seat,
                  commandId);
          if (!previous.isEmpty()) {
            if (!previous.getFirst().equals(digest))
              throw new IllegalArgumentException("Command ID reused with different contents");
            return engine.view(g, seat);
          }
          if (g.revision != revision) throw new Conflict();
          action.accept(g, seat);
          save(g);
          jdbc.update(
              "INSERT INTO commands(room_id,seat,command_id,digest,revision) VALUES (?,?,?,?,?)",
              id,
              seat,
              commandId,
              digest,
              g.revision);
          event(g, seat);
          return engine.view(g, seat);
        });
  }

  public List<Map<String, Object>> list(String guest) {
    return jdbc.query(
        "SELECT r.id,r.snapshot->>'status' AS status, COALESCE(r.snapshot->>'format','COMMANDER') AS format FROM rooms r JOIN members m ON m.room_id=r.id WHERE m.guest_hash=? ORDER BY r.updated_at DESC LIMIT 30",
        (rs, row) ->
            Map.<String, Object>of(
                "id",
                rs.getString("id"),
                "status",
                rs.getString("status"),
                "format",
                rs.getString("format")),
        guest);
  }

  private Game read(String id, boolean lock) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT snapshot::text FROM rooms WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            String.class,
            id);
    if (rows.isEmpty()) throw new IllegalArgumentException("Room not found");
    try {
      return mapper.readValue(rows.getFirst(), Game.class);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot read saved game", e);
    }
  }

  private void save(Game g) {
    jdbc.update(
        "UPDATE rooms SET snapshot=CAST(? AS jsonb),updated_at=now() WHERE id=?", json(g), g.id);
  }

  private void event(Game g, int seat) {
    jdbc.update(
        "INSERT INTO actions(room_id,revision,seat,description) VALUES (?,?,?,?)",
        g.id,
        g.revision,
        seat,
        g.log.getLast());
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid state", e);
    }
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static class Forbidden extends IllegalArgumentException {
    public Forbidden(String message) {
      super(message);
    }
  }
}
