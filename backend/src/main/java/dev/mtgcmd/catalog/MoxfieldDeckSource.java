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

  MoxfieldDeckSource(final DeckImporter.Fetcher fetcher) {
    this.fetcher = fetcher;
  }

  ParsedDeck parse(final String input) {
    final MoxfieldDeckUrl url = MoxfieldDeckUrl.parse(input);
    final JsonNode root = fetchRoot(url);
    requireObject(root);
    final JsonNode boards = root.has("boards") ? root.get("boards") : root;
    requireObject(boards);
    final List<ParsedDeck.Entry> entries = new ArrayList<>();
    for (final String board : List.of("commanders", "mainboard")) {
      if (!boards.has(board)) {
        continue;
      }
      final JsonNode cards = boardEntries(boards.get(board));
      for (final JsonNode card : cards) {
        entries.add(parseEntry(card, "commanders".equals(board)));
      }
    }
    if (entries.isEmpty()) {
      throw new IllegalArgumentException(
          "Moxfield returned no cards. Export and paste its text instead.");
    }
    return ParsedDeck.from(entries);
  }

  // withMessage preserves the failure subtype and chains the caught exception as its cause.
  // DeckImporterTest verifies every subtype and its complete cause chain.
  @SuppressWarnings("PMD.PreserveStackTrace")
  private JsonNode fetchRoot(final MoxfieldDeckUrl url) {
    final String json;
    try {
      json = fetcher.get(url.apiUri());
    } catch (CardServiceException e) {
      throw e.withMessage(UNAVAILABLE);
    }
    if (json == null || json.isBlank()) {
      throw unsupported();
    }
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new InvalidCardServiceResponseException(UNAVAILABLE, e);
    }
  }

  private JsonNode boardEntries(final JsonNode board) {
    requireContainer(board);
    final JsonNode entries = board.isObject() && board.has("cards") ? board.get("cards") : board;
    requireContainer(entries);
    return entries;
  }

  private ParsedDeck.Entry parseEntry(final JsonNode entry, final boolean commander) {
    requireObject(entry);
    final JsonNode card = entry.get("card");
    requireObject(card);
    final JsonNode name = card.get("name");
    if (name == null
        || !name.isTextual()
        || name.textValue().isBlank()
        || name.textValue().length() > DeckImportLimits.MAX_NAME_LENGTH) {
      throw unsupported();
    }
    return new ParsedDeck.Entry(name.textValue(), quantity(entry), commander);
  }

  private int quantity(final JsonNode entry) {
    if (!entry.has("quantity")) {
      return 1;
    }
    final JsonNode quantity = entry.get("quantity");
    if (!quantity.isIntegralNumber()
        || !quantity.canConvertToInt()
        || !DeckImportLimits.validQuantity(quantity.intValue())) {
      throw unsupported();
    }
    return quantity.intValue();
  }

  private void requireObject(final JsonNode node) {
    if (node == null || !node.isObject()) {
      throw unsupported();
    }
  }

  private void requireContainer(final JsonNode node) {
    if (node == null || !(node.isObject() || node.isArray())) {
      throw unsupported();
    }
  }

  private InvalidCardServiceResponseException unsupported() {
    return new InvalidCardServiceResponseException(UNSUPPORTED, null);
  }
}
