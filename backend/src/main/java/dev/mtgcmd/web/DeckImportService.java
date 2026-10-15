package dev.mtgcmd.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.mtgcmd.catalog.DeckImporter;
import dev.mtgcmd.game.Game.Definition;
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

  public DeckImportService(
      final RoomStore rooms, final DeckImporter importer, final DeckDraftRepository drafts) {
    this.rooms = rooms;
    this.importer = importer;
    this.drafts = drafts;
  }

  public StoredPreview preview(
      final String guest, final String room, final String text, final String url)
      throws JsonProcessingException {
    rooms.seat(guest, room);
    final DeckImporter.Draft draft = importer.preview(text, url, rooms.format(guest, room));
    final String draftId = UUID.randomUUID().toString();
    drafts.save(guest, draftId, draft.cards());
    return new StoredPreview(draftId, draft);
  }

  public Map<String, Object> load(
      final String guest,
      final String room,
      final String draftId,
      final List<String> commanders,
      final String commandId,
      final long revision)
      throws JsonProcessingException {
    rooms.seat(guest, room);
    if (rooms.format(guest, room).usesCommanders()
        && (commanders == null || commanders.isEmpty())) {
      throw new IllegalArgumentException("Choose commanders");
    }
    final List<Definition> cards = drafts.requireAvailable(guest, draftId);
    return rooms.load(guest, room, commandId, revision, cards, commanders);
  }
}
