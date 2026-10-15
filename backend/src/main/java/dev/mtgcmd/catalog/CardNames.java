package dev.mtgcmd.catalog;

import java.util.Locale;

/** Shared identity normalization for lookups, matches, and persisted aliases. */
final class CardNames {
  private CardNames() {}

  static String key(final String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
}
