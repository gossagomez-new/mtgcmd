package dev.mtgcmd.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Deck HTTP translation and successful-load notifications. */
@RestController
@RequestMapping("/api")
public final class DeckController {
  private final GuestSessions guests;
  private final DeckImportService imports;
  private final GameSocket socket;

  public DeckController(GuestSessions guests, DeckImportService imports, GameSocket socket) {
    this.guests = guests;
    this.imports = imports;
    this.socket = socket;
  }

  public record Import(String text, String url) {}

  public record Deck(String draftId, List<String> commanders, String commandId, long revision) {}

  @PostMapping("/rooms/{id}/decks/preview")
  public Map<String, Object> preview(
      HttpServletRequest r, @PathVariable String id, @RequestBody Import request) throws Exception {
    var preview = imports.preview(guest(r), id, request.text(), request.url());
    var draft = preview.draft();
    return Map.of(
        "draftId",
        preview.draftId(),
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
    var view =
        imports.load(
            guest(r),
            id,
            request.draftId(),
            request.commanders(),
            request.commandId(),
            request.revision());
    socket.broadcast(id);
    return view;
  }

  private String guest(HttpServletRequest r) {
    return guests.require(r).hash();
  }
}
