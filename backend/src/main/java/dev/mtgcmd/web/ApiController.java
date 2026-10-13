package dev.mtgcmd.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.catalog.DeckImporter;
import dev.mtgcmd.game.Game;
import dev.mtgcmd.store.RoomStore;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final GuestSessions guests;
  private final RoomStore rooms;
  private final DeckImporter importer;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final GameSocket socket;

  public ApiController(
      GuestSessions guests,
      RoomStore rooms,
      DeckImporter importer,
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      GameSocket socket) {
    this.guests = guests;
    this.rooms = rooms;
    this.importer = importer;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.socket = socket;
  }

  public record Name(String name) {}

  public record Import(String text, String url) {}

  public record Deck(String draftId, List<String> commanders, String commandId, long revision) {}

  @GetMapping("/session")
  public Map<String, String> session(HttpServletRequest request, HttpServletResponse response) {
    return Map.of("csrf", guests.bootstrap(request, response).csrf());
  }

  @GetMapping("/rooms")
  public List<Map<String, Object>> list(HttpServletRequest r) {
    return rooms.list(guest(r));
  }

  @PostMapping("/rooms")
  public Map<String, Object> create(HttpServletRequest r, @RequestBody Name name) {
    return rooms.create(guest(r), name.name());
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

  @PostMapping("/rooms/{id}/decks/preview")
  public Map<String, Object> preview(
      HttpServletRequest r, @PathVariable String id, @RequestBody Import request) throws Exception {
    String guest = guest(r);
    rooms.seat(guest, id);
    var draft = importer.preview(request.text(), request.url());
    String draftId = UUID.randomUUID().toString();
    jdbc.update(
        "DELETE FROM deck_drafts WHERE guest_hash=? AND created_at<now()-interval '1 day'", guest);
    jdbc.update(
        "INSERT INTO deck_drafts(id,guest_hash,cards) VALUES (?,?,CAST(? AS jsonb))",
        draftId,
        guest,
        mapper.writeValueAsString(draft.cards()));
    return Map.of(
        "draftId",
        draftId,
        "choices",
        draft.choices(),
        "suggested",
        draft.suggested(),
        "count",
        draft.cards().size());
  }

  @PostMapping("/rooms/{id}/deck")
  public Map<String, Object> deck(
      HttpServletRequest r, @PathVariable String id, @RequestBody Deck request) throws Exception {
    String guest = guest(r);
    rooms.seat(guest, id);
    if (request.commanders() == null || request.commanders().isEmpty())
      throw new IllegalArgumentException("Choose commanders");
    var rows =
        jdbc.queryForList(
            "SELECT cards::text FROM deck_drafts WHERE id=? AND guest_hash=? AND created_at>now()-interval '1 day'",
            String.class,
            request.draftId(),
            guest);
    if (rows.isEmpty()) throw new IllegalArgumentException("Deck preview expired; preview again");
    List<Game.Definition> cards = mapper.readValue(rows.getFirst(), new TypeReference<>() {});
    var v =
        rooms.load(guest, id, request.commandId(), request.revision(), cards, request.commanders());
    socket.broadcast(id);
    return v;
  }

  private String guest(HttpServletRequest r) {
    return guests.require(r).hash();
  }

  @ExceptionHandler(RoomStore.Conflict.class)
  public ResponseEntity<?> conflict(RoomStore.Conflict e) {
    return ResponseEntity.status(409).body(Map.of("message", "Table changed; refresh and retry"));
  }

  @ExceptionHandler(RoomStore.Forbidden.class)
  public ResponseEntity<?> forbidden(RoomStore.Forbidden e) {
    return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> invalid(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> failed(Exception e) {
    return ResponseEntity.status(500)
        .body(
            Map.of(
                "message",
                "Request failed; saved state was not acknowledged. Refresh before retrying."));
  }
}
