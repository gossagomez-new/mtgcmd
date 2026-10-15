package dev.mtgcmd.catalog;

import java.util.*;

enum DeckSection {
  COMMANDER(true, true, "commander", "commanders"),
  MAINBOARD(true, false, "deck", "mainboard"),
  SIDEBOARD(false, false, "sideboard"),
  MAYBEBOARD(false, false, "maybeboard"),
  CONSIDERING(false, false, "considering");

  private final boolean included;
  private final boolean commander;
  private final Set<String> headers;

  DeckSection(final boolean included, final boolean commander, final String... headers) {
    this.included = included;
    this.commander = commander;
    this.headers = Set.of(headers);
  }

  boolean included() {
    return included;
  }

  boolean commander() {
    return commander;
  }

  static Optional<DeckSection> fromHeader(final String value) {
    final String header = value.toLowerCase(Locale.ROOT).replace(":", "");
    return Arrays.stream(values()).filter(section -> section.headers.contains(header)).findFirst();
  }
}
