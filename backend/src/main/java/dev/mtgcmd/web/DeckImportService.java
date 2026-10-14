package dev.mtgcmd.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.mtgcmd.catalog.DeckImporter;
import dev.mtgcmd.store.DeckDraftRepository;
import dev.mtgcmd.store.RoomStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deck workflow coordination; RoomStore retains revision/idempotency and transaction ownership. */
public final class DeckImportService {
  public record StoredPreview(String draftId, DeckImporter.Draft draft) {}

  private final RoomStore rooms;
  private final DeckImporter importer;
  private final DeckDraftRepository drafts;

  public DeckImportService(RoomStore rooms, DeckImporter importer, DeckDraftRepository drafts) {
    this.rooms = rooms;
    this.importer = importer;
    this.drafts = drafts;
  }

  public StoredPreview preview(String guest, String room, String text, String url)
      throws JsonProcessingException {
    rooms.seat(guest, room);
    var draft = importer.preview(text, url, rooms.format(guest, room));
    String draftId = UUID.randomUUID().toString();
    drafts.save(guest, draftId, draft.cards());
    return new StoredPreview(draftId, draft);
  }

  public Map<String, Object> load(
      String guest,
      String room,
      String draftId,
      List<String> commanders,
      String commandId,
      long revision)
      throws JsonProcessingException {
    rooms.seat(guest, room);
    if (rooms.format(guest, room).usesCommanders() && (commanders == null || commanders.isEmpty()))
      throw new IllegalArgumentException("Choose commanders");
    var cards = drafts.requireAvailable(guest, draftId);
    return rooms.load(guest, room, commandId, revision, cards, commanders);
  }
}
