package dev.mtgcmd.catalog;

import java.util.*;

record ParsedDeck(List<Entry> entries) {
  record Entry(String name, int quantity, boolean commander) {}

  ParsedDeck {
    entries = List.copyOf(entries);
  }

  static ParsedDeck from(List<Entry> source) {
    Map<String, Entry> merged = new LinkedHashMap<>();
    long total = 0;
    for (Entry entry : source) {
      if (!DeckImportLimits.validQuantity(entry.quantity()))
        throw new IllegalArgumentException(DeckImportLimits.CARD_COUNT_ERROR);
      total += entry.quantity();
      if (total > DeckImportLimits.MAX_CARDS)
        throw new IllegalArgumentException(DeckImportLimits.CARD_COUNT_ERROR);
      merged.merge(
          entry.name(),
          entry,
          (previous, added) ->
              new Entry(
                  previous.name(),
                  previous.quantity() + added.quantity(),
                  previous.commander() || added.commander()));
    }
    return new ParsedDeck(new ArrayList<>(merged.values()));
  }

  List<String> names() {
    return entries.stream().map(Entry::name).toList();
  }

  void validateTotal() {
    long total = entries.stream().mapToLong(Entry::quantity).sum();
    if (total < 1 || total > DeckImportLimits.MAX_CARDS)
      throw new IllegalArgumentException(DeckImportLimits.CARD_COUNT_ERROR);
  }
}
