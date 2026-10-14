package dev.mtgcmd.web;

import com.fasterxml.jackson.databind.JsonNode;
import dev.mtgcmd.game.Game;
import dev.mtgcmd.game.GameFormat;
import dev.mtgcmd.store.RoomStore;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Room and command HTTP translation; RoomStore owns mutations. */
@RestController
@RequestMapping("/api")
public final class RoomController {
  private final GuestSessions guests;
  private final RoomStore rooms;
  private final GameSocket socket;

  public RoomController(GuestSessions guests, RoomStore rooms, GameSocket socket) {
    this.guests = guests;
    this.rooms = rooms;
    this.socket = socket;
  }

  public record Name(String name) {}

  public record CreateRoom(String name, JsonNode format) {}

  @GetMapping("/rooms")
  public List<Map<String, Object>> list(HttpServletRequest r) {
    return rooms.list(guest(r));
  }

  @PostMapping("/rooms")
  public Map<String, Object> create(HttpServletRequest r, @RequestBody CreateRoom request) {
    GameFormat format = GameFormat.COMMANDER;
    if (request.format() != null && !request.format().isNull()) {
      if (!request.format().isTextual()) throw new IllegalArgumentException("Invalid table format");
      try {
        format = GameFormat.valueOf(request.format().textValue());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Choose COMMANDER or STANDARD", e);
      }
    }
    return rooms.create(guest(r), request.name(), format);
  }

  @PostMapping("/rooms/{id}/join")
  public Map<String, Object> join(
      HttpServletRequest r, @PathVariable String id, @RequestBody Name name) {
    var v = rooms.join(guest(r), id, name.name());
    socket.broadcast(id);
    return v;
  }

  @GetMapping("/rooms/{id}")
  public Map<String, Object> view(HttpServletRequest r, @PathVariable String id) {
    return rooms.view(guest(r), id);
  }

  @PostMapping("/rooms/{id}/commands")
  public Map<String, Object> command(
      HttpServletRequest r, @PathVariable String id, @RequestBody Game.Command command) {
    var v = rooms.execute(guest(r), id, command);
    socket.broadcast(id);
    return v;
  }

  private String guest(HttpServletRequest r) {
    return guests.require(r).hash();
  }
}
