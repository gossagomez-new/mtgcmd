package dev.mtgcmd.catalog;

import java.util.*;
import java.util.regex.*;

final class TextDeckParser {
  private static final Pattern LINE = Pattern.compile("^(\\d+)x?\\s+(.+)$");
  private static final Pattern PRINTING =
      Pattern.compile("\\s+\\([A-Za-z0-9]+\\)\\s+[A-Za-z0-9★-]+(?:\\s+\\*[^*]+\\*)?$");

  ParsedDeck parse(String text) {
    validateText(text);
    List<ParsedDeck.Entry> entries = new ArrayList<>();
    DeckSection section = DeckSection.MAINBOARD;
    int lineNumber = 0;
    for (String raw : text.split("\\R")) {
      lineNumber++;
      String value = raw.trim();
      if (isBlankOrComment(value)) continue;
      Optional<DeckSection> header = DeckSection.fromHeader(value);
      if (header.isPresent()) {
        section = header.get();
        continue;
      }
      if (section.included()) entries.add(parseEntry(value, lineNumber, section));
    }
    return ParsedDeck.from(entries);
  }

  private void validateText(String text) {
    if (text == null || text.isBlank()) throw new IllegalArgumentException("Decklist is empty");
    if (text.length() > DeckImportLimits.MAX_TEXT_LENGTH)
      throw new IllegalArgumentException("Decklist is too large");
  }

  private boolean isBlankOrComment(String value) {
    return value.isEmpty() || value.startsWith("#") || value.startsWith("//");
  }

  private ParsedDeck.Entry parseEntry(String value, int lineNumber, DeckSection section) {
    Matcher match = LINE.matcher(value);
    if (!match.matches())
      throw new IllegalArgumentException(
          "Invalid decklist line " + lineNumber + ": use quantity and card name");
    int count;
    try {
      count = Integer.parseInt(match.group(1));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Deck exceeds 250 cards");
    }
    if (!DeckImportLimits.validQuantity(count))
      throw new IllegalArgumentException(DeckImportLimits.CARD_COUNT_ERROR);
    String name = PRINTING.matcher(match.group(2)).replaceFirst("").trim();
    if (name.length() > DeckImportLimits.MAX_NAME_LENGTH)
      throw new IllegalArgumentException("Card name is too long");
    return new ParsedDeck.Entry(name, count, section.commander());
  }
}
