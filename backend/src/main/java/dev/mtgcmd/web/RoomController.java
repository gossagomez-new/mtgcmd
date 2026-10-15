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

  public RoomController(
      final GuestSessions guests, final RoomStore rooms, final GameSocket socket) {
    this.guests = guests;
    this.rooms = rooms;
    this.socket = socket;
  }

  public record Name(String name) {}

  public record CreateRoom(String name, JsonNode format) {}

  @GetMapping("/rooms")
  public List<Map<String, Object>> list(final HttpServletRequest httpRequest) {
    return rooms.list(guest(httpRequest));
  }

  @PostMapping("/rooms")
  public Map<String, Object> create(
      final HttpServletRequest httpRequest, @RequestBody final CreateRoom request) {
    GameFormat format = GameFormat.COMMANDER;
    if (request.format() != null && !request.format().isNull()) {
      if (!request.format().isTextual()) {
        throw new IllegalArgumentException("Invalid table format");
      }
      try {
        format = GameFormat.valueOf(request.format().textValue());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Choose COMMANDER or STANDARD", e);
      }
    }
    return rooms.create(guest(httpRequest), request.name(), format);
  }

  @PostMapping("/rooms/{id}/join")
  public Map<String, Object> join(
      final HttpServletRequest httpRequest,
      @PathVariable("id") final String roomId,
      @RequestBody final Name name) {
    final Map<String, Object> view = rooms.join(guest(httpRequest), roomId, name.name());
    socket.broadcast(roomId);
    return view;
  }

  @GetMapping("/rooms/{id}")
  public Map<String, Object> view(
      final HttpServletRequest httpRequest, @PathVariable("id") final String roomId) {
    return rooms.view(guest(httpRequest), roomId);
  }

  @PostMapping("/rooms/{id}/commands")
  public Map<String, Object> command(
      final HttpServletRequest httpRequest,
      @PathVariable("id") final String roomId,
      @RequestBody final Game.Command command) {
    final Map<String, Object> view = rooms.execute(guest(httpRequest), roomId, command);
    socket.broadcast(roomId);
    return view;
  }

  private String guest(final HttpServletRequest httpRequest) {
    return guests.require(httpRequest).hash();
  }
}
