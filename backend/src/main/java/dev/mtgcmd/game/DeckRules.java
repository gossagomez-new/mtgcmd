package dev.mtgcmd.game;

import static dev.mtgcmd.game.Game.*;

import java.time.Clock;
import java.util.*;

final class DeckRules {
  private DeckRules() {}

  static List<String> validate(
      GameFormat format, List<Definition> deck, List<String> commanders, Clock clock) {
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
      List<Definition> deck, List<String> commanders, Clock clock) {
    check(commanders == null || commanders.isEmpty(), "Standard does not use commanders");
    List<String> warnings = new ArrayList<>();
    Map<String, Integer> copies = new HashMap<>();
    for (Definition def : deck) {
      if (!CardLegality.fresh(def, "standard", clock))
        warnings.add(
            "Standard legality for "
                + def.name()
                + " could not be verified; check current legality before playing.");
      else if (!"legal".equals(def.legalities().get("standard")))
        warnings.add(def.name() + " is not listed as Standard legal.");
      int count = copies.merge(def.id(), 1, Integer::sum);
      boolean basic = Arrays.asList(def.type().split("—", 2)[0].split("\\s+")).contains("Basic");
      if (count > 4
          && !basic
          && !def.text().toLowerCase(Locale.ROOT).contains("any number of cards named"))
        warnings.add(def.name() + " exceeds four copies; check any card-specific deck exception.");
    }
    return warnings.stream().distinct().toList();
  }

  private static List<String> commander(List<Definition> deck, List<String> commanders) {
    List<Definition> selected = resolveCommanders(deck, commanders);
    List<String> warnings = new ArrayList<>();
    appendDeckSizeWarning(deck.size(), warnings);
    appendCommanderWarnings(selected, warnings);
    appendCardWarnings(deck, combinedColorIdentity(selected), warnings);
    return warnings.stream().distinct().toList();
  }

  private static List<Definition> resolveCommanders(
      List<Definition> deck, List<String> commanders) {
    check(
        commanders != null && !commanders.isEmpty() && commanders.size() <= 2,
        "Choose one or two commanders");
    check(new HashSet<>(commanders).size() == commanders.size(), "Commanders must be distinct");
    return commanders.stream().map(id -> findCommander(deck, id)).toList();
  }

  private static Definition findCommander(List<Definition> deck, String id) {
    return deck.stream()
        .filter(card -> card.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Commander must be in the deck"));
  }

  private static void appendDeckSizeWarning(int size, List<String> warnings) {
    if (size != 100) {
      warnings.add(
          "Deck contains " + size + " cards; Commander normally uses 100 including commanders.");
    }
  }

  private static void appendCommanderWarnings(List<Definition> selected, List<String> warnings) {
    for (Definition card : selected) {
      if (!card.type().contains("Legendary") || !card.type().contains("Creature")) {
        warnings.add("Check commander eligibility for " + card.name() + ".");
      }
    }
    if (selected.size() == 2) {
      warnings.add("Check that the chosen commanders can be paired.");
    }
  }

  private static Set<String> combinedColorIdentity(List<Definition> selected) {
    Set<String> identity = new HashSet<>();
    for (Definition card : selected) {
      identity.addAll(card.colors());
    }
    return identity;
  }

  private static void appendCardWarnings(
      List<Definition> deck, Set<String> identity, List<String> warnings) {
    Set<String> seen = new HashSet<>();
    for (Definition card : deck) {
      if (!card.legal()) {
        warnings.add(card.name() + " is not listed as Commander legal.");
      }
      if (!identity.containsAll(card.colors())) {
        warnings.add(card.name() + " exceeds the chosen color identity.");
      }
      boolean duplicate = !seen.add(card.id());
      if (duplicate && !allowsRepeatedCopies(card)) {
        warnings.add("Check singleton exception for " + card.name() + ".");
      }
    }
  }

  private static boolean allowsRepeatedCopies(Definition card) {
    return card.type().contains("Basic") || card.text().contains("any number of cards named");
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}
