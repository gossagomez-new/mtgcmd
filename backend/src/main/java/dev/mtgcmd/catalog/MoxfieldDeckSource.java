package dev.mtgcmd.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import java.util.*;

final class MoxfieldDeckSource {
  private static final String UNSUPPORTED =
      "Moxfield returned an unsupported deck. Export and paste its text instead.";
  private static final String UNAVAILABLE =
      "Moxfield is unavailable or the deck is private. Export the deck and paste its text instead.";
  private final DeckImporter.Fetcher fetcher;
  private final ObjectMapper mapper = new ObjectMapper();

  MoxfieldDeckSource(DeckImporter.Fetcher fetcher) {
    this.fetcher = fetcher;
  }

  ParsedDeck parse(String input) {
    MoxfieldDeckUrl url = MoxfieldDeckUrl.parse(input);
    JsonNode root = fetchRoot(url);
    requireObject(root);
    JsonNode boards = root.has("boards") ? root.get("boards") : root;
    requireObject(boards);
    List<ParsedDeck.Entry> entries = new ArrayList<>();
    for (String board : List.of("commanders", "mainboard")) {
      if (!boards.has(board)) continue;
      JsonNode cards = boardEntries(boards.get(board));
      for (JsonNode card : cards) entries.add(parseEntry(card, board.equals("commanders")));
    }
    if (entries.isEmpty())
      throw new IllegalArgumentException(
          "Moxfield returned no cards. Export and paste its text instead.");
    return ParsedDeck.from(entries);
  }

  private JsonNode fetchRoot(MoxfieldDeckUrl url) {
    String json;
    try {
      json = fetcher.get(url.apiUri());
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(UNAVAILABLE);
    }
    if (json == null || json.isBlank()) throw unsupported();
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(UNAVAILABLE);
    }
  }

  private JsonNode boardEntries(JsonNode board) {
    requireContainer(board);
    JsonNode entries = board.isObject() && board.has("cards") ? board.get("cards") : board;
    requireContainer(entries);
    return entries;
  }

  private ParsedDeck.Entry parseEntry(JsonNode entry, boolean commander) {
    requireObject(entry);
    JsonNode card = entry.get("card");
    requireObject(card);
    JsonNode name = card.get("name");
    if (name == null
        || !name.isTextual()
        || name.textValue().isBlank()
        || name.textValue().length() > DeckImportLimits.MAX_NAME_LENGTH) throw unsupported();
    return new ParsedDeck.Entry(name.textValue(), quantity(entry), commander);
  }

  private int quantity(JsonNode entry) {
    if (!entry.has("quantity")) return 1;
    JsonNode quantity = entry.get("quantity");
    if (!quantity.isIntegralNumber()
        || !quantity.canConvertToInt()
        || !DeckImportLimits.validQuantity(quantity.intValue())) throw unsupported();
    return quantity.intValue();
  }

  private void requireObject(JsonNode node) {
    if (node == null || !node.isObject()) throw unsupported();
  }

  private void requireContainer(JsonNode node) {
    if (node == null || !(node.isObject() || node.isArray())) throw unsupported();
  }

  private IllegalArgumentException unsupported() {
    return new IllegalArgumentException(UNSUPPORTED);
  }
}
