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
    check(
        commanders != null && !commanders.isEmpty() && commanders.size() <= 2,
        "Choose one or two commanders");
    check(new HashSet<>(commanders).size() == commanders.size(), "Commanders must be distinct");
    for (String id : commanders)
      check(deck.stream().anyMatch(d -> d.id().equals(id)), "Commander must be in the deck");
    List<String> warnings = new ArrayList<>();
    if (deck.size() != 100)
      warnings.add(
          "Deck contains "
              + deck.size()
              + " cards; Commander normally uses 100 including commanders.");
    Set<String> identity = new HashSet<>();
    for (String id : commanders) {
      Definition def = deck.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
      identity.addAll(def.colors());
      if (!def.type().contains("Legendary") || !def.type().contains("Creature"))
        warnings.add("Check commander eligibility for " + def.name() + ".");
    }
    if (commanders.size() == 2) warnings.add("Check that the chosen commanders can be paired.");
    Set<String> seen = new HashSet<>();
    for (Definition def : deck) {
      if (!def.legal()) warnings.add(def.name() + " is not listed as Commander legal.");
      if (!identity.containsAll(def.colors()))
        warnings.add(def.name() + " exceeds the chosen color identity.");
      if (!seen.add(def.id())
          && !def.type().contains("Basic")
          && !def.text().contains("any number of cards named"))
        warnings.add("Check singleton exception for " + def.name() + ".");
    }
    return warnings.stream().distinct().toList();
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}
