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
      final JdbcTemplate jdbc,
      final TransactionTemplate transactions,
      final ObjectMapper mapper,
      final GameEngine engine) {
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

  public Map<String, Object> create(final String guest, final String name) {
    return create(guest, name, GameFormat.COMMANDER);
  }

  public Map<String, Object> create(
      final String guest, final String name, final GameFormat format) {
    return transactions.execute(
        ignored -> {
          final Game game = new Game();
          game.format = format == null ? GameFormat.COMMANDER : format;
          engine.join(game, name);
          jdbc.update(
              "INSERT INTO rooms(id,snapshot) VALUES (?,CAST(? AS jsonb))", game.id, json(game));
          jdbc.update(
              "INSERT INTO members(room_id,guest_hash,seat) VALUES (?,?,0)", game.id, guest);
          event(game, 0);
          return engine.view(game, 0);
        });
  }

  public Map<String, Object> join(final String guest, final String roomId, final String name) {
    return transactions.execute(
        ignored -> {
          final Game game = read(roomId, true);
          final List<Integer> seats =
              jdbc.queryForList(
                  "SELECT seat FROM members WHERE room_id=? AND guest_hash=?",
                  Integer.class,
                  roomId,
                  guest);
          if (!seats.isEmpty()) {
            return engine.view(game, seats.getFirst());
          }
          engine.join(game, name);
          final int seat = game.players.size() - 1;
          jdbc.update(
              "INSERT INTO members(room_id,guest_hash,seat) VALUES (?,?,?)", roomId, guest, seat);
          save(game);
          event(game, seat);
          return engine.view(game, seat);
        });
  }

  public GameFormat format(final String guest, final String identifier) {
    seat(guest, identifier);
    return read(identifier, false).format();
  }

  public Map<String, Object> view(final String guest, final String roomId) {
    final int seat = seat(guest, roomId);
    return engine.view(read(roomId, false), seat);
  }

  public int seat(final String guest, final String roomId) {
    final List<Integer> seats =
        jdbc.queryForList(
            "SELECT seat FROM members WHERE room_id=? AND guest_hash=?",
            Integer.class,
            roomId,
            guest);
    if (seats.isEmpty()) {
      throw new Forbidden("You are not a room member");
    }
    return seats.getFirst();
  }

  public Map<String, Object> execute(
      final String guest, final String identifier, final Game.Command command) {
    if (command == null || command.type() == null || command.data() == null) {
      throw new IllegalArgumentException("Invalid command");
    }
    return mutate(
        guest,
        identifier,
        command.id(),
        command.revision(),
        json(command),
        (g, s) -> engine.apply(g, s, command));
  }

  public Map<String, Object> load(
      final String guest,
      final String roomId,
      final String commandId,
      final long revision,
      final List<Game.Definition> cards,
      final List<String> commanders) {
    return mutate(
        guest,
        roomId,
        commandId,
        revision,
        json(Map.of("cards", cards, "commanders", commanders == null ? List.of() : commanders)),
        (g, s) -> engine.load(g, s, cards, commanders));
  }

  private Map<String, Object> mutate(
      final String guest,
      final String identifier,
      final String commandId,
      final long revision,
      final String payload,
      final BiConsumer<Game, Integer> action) {
    GameEngine.clean(commandId, "command ID", 80);
    return transactions.execute(
        ignored -> {
          final Game game = read(identifier, true);
          final int seat = seat(guest, identifier);
          final String digest = hash(payload);
          final List<String> previous =
              jdbc.queryForList(
                  "SELECT digest FROM commands WHERE room_id=? AND seat=? AND command_id=?",
                  String.class,
                  identifier,
                  seat,
                  commandId);
          if (!previous.isEmpty()) {
            if (!previous.getFirst().equals(digest)) {
              throw new IllegalArgumentException("Command ID reused with different contents");
            }
            return engine.view(game, seat);
          }
          if (game.revision != revision) {
            throw new Conflict();
          }
          action.accept(game, seat);
          save(game);
          jdbc.update(
              "INSERT INTO commands(room_id,seat,command_id,digest,revision) VALUES (?,?,?,?,?)",
              identifier,
              seat,
              commandId,
              digest,
              game.revision);
          event(game, seat);
          return engine.view(game, seat);
        });
  }

  public List<Map<String, Object>> list(final String guest) {
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

  private Game read(final String identifier, final boolean lock) {
    final List<String> rows =
        jdbc.queryForList(
            "SELECT snapshot::text FROM rooms WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            String.class,
            identifier);
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("Room not found");
    }
    try {
      return mapper.readValue(rows.getFirst(), Game.class);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot read saved game", e);
    }
  }

  private void save(final Game game) {
    jdbc.update(
        "UPDATE rooms SET snapshot=CAST(? AS jsonb),updated_at=now() WHERE id=?",
        json(game),
        game.id);
  }

  private void event(final Game game, final int seat) {
    jdbc.update(
        "INSERT INTO actions(room_id,revision,seat,description) VALUES (?,?,?,?)",
        game.id,
        game.revision,
        seat,
        game.log.getLast());
  }

  private String json(final Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid state", e);
    }
  }

  public static String hash(final String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static class Forbidden extends IllegalArgumentException {
    public Forbidden(final String message) {
      super(message);
    }
  }
}
