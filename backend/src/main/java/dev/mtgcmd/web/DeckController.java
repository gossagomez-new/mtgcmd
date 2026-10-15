package dev.mtgcmd.web;

import dev.mtgcmd.catalog.DeckImporter.Draft;
import dev.mtgcmd.web.DeckImportService.StoredPreview;
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

  public DeckController(
      final GuestSessions guests, final DeckImportService imports, final GameSocket socket) {
    this.guests = guests;
    this.imports = imports;
    this.socket = socket;
  }

  public record Import(String text, String url) {}

  public record Deck(String draftId, List<String> commanders, String commandId, long revision) {}

  @PostMapping("/rooms/{id}/decks/preview")
  public Map<String, Object> preview(
      final HttpServletRequest httpRequest,
      @PathVariable("id") final String roomId,
      @RequestBody final Import request)
      throws Exception {
    final StoredPreview preview =
        imports.preview(guest(httpRequest), roomId, request.text(), request.url());
    final Draft draft = preview.draft();
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
      final HttpServletRequest httpRequest,
      @PathVariable("id") final String roomId,
      @RequestBody final Deck request)
      throws Exception {
    final Map<String, Object> view =
        imports.load(
            guest(httpRequest),
            roomId,
            request.draftId(),
            request.commanders(),
            request.commandId(),
            request.revision());
    socket.broadcast(roomId);
    return view;
  }

  private String guest(final HttpServletRequest httpRequest) {
    return guests.require(httpRequest).hash();
  }
}
