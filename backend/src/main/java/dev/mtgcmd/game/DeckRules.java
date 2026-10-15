package dev.mtgcmd.game;

import dev.mtgcmd.game.Game.Definition;
import java.time.Clock;
import java.util.*;

final class DeckRules {
  private DeckRules() {}

  static List<String> validate(
      final GameFormat format,
      final List<Definition> deck,
      final List<String> commanders,
      final Clock clock) {
    check(
        deck != null
            && deck.size() >= format.minimumDeckSize()
            && deck.size() <= format.maximumDeckSize(),
        format == GameFormat.STANDARD
            ? "Standard decks must contain 60–250 cards"
            : "Deck must contain 1–250 cards");
    return format == GameFormat.STANDARD
        ? standard(deck, commanders, clock)
        : commander(deck, commanders);
  }

  private static List<String> standard(
      final List<Definition> deck, final List<String> commanders, final Clock clock) {
    check(commanders == null || commanders.isEmpty(), "Standard does not use commanders");
    final List<String> warnings = new ArrayList<>();
    final Map<String, Integer> copies = new HashMap<>();
    for (final Definition def : deck) {
      if (!CardLegality.fresh(def, "standard", clock)) {
        warnings.add(
            "Standard legality for "
                + def.name()
                + " could not be verified; check current legality before playing.");
      } else if (!"legal".equals(def.legalities().get("standard"))) {
        warnings.add(def.name() + " is not listed as Standard legal.");
      }
      final int count = copies.merge(def.id(), 1, Integer::sum);
      final boolean basic =
          Arrays.asList(def.type().split("—", 2)[0].split("\\s+")).contains("Basic");
      if (count > 4
          && !basic
          && !def.text().toLowerCase(Locale.ROOT).contains("any number of cards named")) {
        warnings.add(def.name() + " exceeds four copies; check any card-specific deck exception.");
      }
    }
    return warnings.stream().distinct().toList();
  }

  private static List<String> commander(
      final List<Definition> deck, final List<String> commanders) {
    final List<Definition> selected = resolveCommanders(deck, commanders);
    final List<String> warnings = new ArrayList<>();
    appendDeckSizeWarning(deck.size(), warnings);
    appendCommanderWarnings(selected, warnings);
    appendCardWarnings(deck, combinedColorIdentity(selected), warnings);
    return warnings.stream().distinct().toList();
  }

  private static List<Definition> resolveCommanders(
      final List<Definition> deck, final List<String> commanders) {
    check(
        commanders != null && !commanders.isEmpty() && commanders.size() <= 2,
        "Choose one or two commanders");
    check(new HashSet<>(commanders).size() == commanders.size(), "Commanders must be distinct");
    return commanders.stream().map(id -> findCommander(deck, id)).toList();
  }

  private static Definition findCommander(final List<Definition> deck, final String identifier) {
    return deck.stream()
        .filter(card -> card.id().equals(identifier))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Commander must be in the deck"));
  }

  private static void appendDeckSizeWarning(final int size, final List<String> warnings) {
    if (size != 100) {
      warnings.add(
          "Deck contains " + size + " cards; Commander normally uses 100 including commanders.");
    }
  }

  private static void appendCommanderWarnings(
      final List<Definition> selected, final List<String> warnings) {
    for (final Definition card : selected) {
      if (!card.type().contains("Legendary") || !card.type().contains("Creature")) {
        warnings.add("Check commander eligibility for " + card.name() + ".");
      }
    }
    if (selected.size() == 2) {
      warnings.add("Check that the chosen commanders can be paired.");
    }
  }

  private static Set<String> combinedColorIdentity(final List<Definition> selected) {
    final Set<String> identity = new HashSet<>();
    for (final Definition card : selected) {
      identity.addAll(card.colors());
    }
    return identity;
  }

  private static void appendCardWarnings(
      final List<Definition> deck, final Set<String> identity, final List<String> warnings) {
    final Set<String> seen = new HashSet<>();
    for (final Definition card : deck) {
      if (!card.legal()) {
        warnings.add(card.name() + " is not listed as Commander legal.");
      }
      if (!identity.containsAll(card.colors())) {
        warnings.add(card.name() + " exceeds the chosen color identity.");
      }
      final boolean duplicate = !seen.add(card.id());
      if (duplicate && !allowsRepeatedCopies(card)) {
        warnings.add("Check singleton exception for " + card.name() + ".");
      }
    }
  }

  private static boolean allowsRepeatedCopies(final Definition card) {
    return card.type().contains("Basic") || card.text().contains("any number of cards named");
  }

  private static void check(final boolean condition, final String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
