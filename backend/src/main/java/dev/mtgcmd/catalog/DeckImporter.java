package dev.mtgcmd.catalog;

import com.fasterxml.jackson.databind.*;
import dev.mtgcmd.game.Game.Definition;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

public class DeckImporter {
  @FunctionalInterface
  public interface Catalog {
    List<Definition> resolve(List<String> names);
  }

  @FunctionalInterface
  public interface Fetcher {
    String get(URI uri);
  }

  public record Draft(List<Definition> cards, List<Definition> choices, List<String> suggested) {}

  private final Catalog catalog;
  private final Fetcher fetcher;

  public DeckImporter(Catalog catalog, Fetcher fetcher) {
    this.catalog = catalog;
    this.fetcher = fetcher;
  }

  public Draft preview(String text, String url) {
    Map<String, Integer> quantities = new LinkedHashMap<>();
    List<String> suggested = new ArrayList<>();
    if (url != null && !url.isBlank()) {
      URI uri;
      try {
        uri = URI.create(url.trim());
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid Moxfield URL");
      }
      if (!"https".equals(uri.getScheme())
          || !Set.of("moxfield.com", "www.moxfield.com")
              .contains(uri.getHost() == null ? "" : uri.getHost())
          || uri.getUserInfo() != null
          || uri.getPort() != -1
          || !uri.getPath().matches("/decks/[A-Za-z0-9_-]{1,80}/?"))
        throw new IllegalArgumentException("Use a public HTTPS Moxfield deck URL");
      String deckId = uri.getPath().split("/")[2];
      JsonNode root;
      try {
        root =
            new ObjectMapper()
                .readTree(
                    fetcher.get(URI.create("https://api2.moxfield.com/v3/decks/all/" + deckId)));
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Moxfield is unavailable or the deck is private. Export the deck and paste its text instead.");
      }
      JsonNode boards = root.has("boards") ? root.path("boards") : root;
      for (String board : List.of("commanders", "mainboard")) {
        JsonNode entries = boards.path(board);
        if (entries.has("cards")) entries = entries.path("cards");
        for (JsonNode entry : entries) {
          String name = entry.path("card").path("name").asText("");
          int count = entry.path("quantity").asInt(1);
          if (name.isBlank() || count < 1 || count > 250)
            throw new IllegalArgumentException(
                "Moxfield returned an unsupported deck. Export and paste its text instead.");
          quantities.merge(name, count, Integer::sum);
          if (board.equals("commanders")) suggested.add(name);
        }
      }
      if (quantities.isEmpty())
        throw new IllegalArgumentException(
            "Moxfield returned no cards. Export and paste its text instead.");
    } else {
      if (text == null || text.isBlank()) throw new IllegalArgumentException("Decklist is empty");
      if (text.length() > 100000) throw new IllegalArgumentException("Decklist is too large");
      String section = "deck";
      int lineNumber = 0;
      Pattern line = Pattern.compile("^(\\d+)x?\\s+(.+)$");
      for (String raw : text.split("\\R")) {
        lineNumber++;
        String value = raw.trim();
        if (value.isEmpty() || value.startsWith("#") || value.startsWith("//")) continue;
        String header = value.toLowerCase(Locale.ROOT).replace(":", "");
        if (Set.of(
                "commander",
                "commanders",
                "deck",
                "mainboard",
                "sideboard",
                "maybeboard",
                "considering")
            .contains(header)) {
          section = header;
          continue;
        }
        if (Set.of("sideboard", "maybeboard", "considering").contains(section)) continue;
        Matcher match = line.matcher(value);
        if (!match.matches())
          throw new IllegalArgumentException(
              "Invalid decklist line " + lineNumber + ": use quantity and card name");
        int count;
        try {
          count = Integer.parseInt(match.group(1));
        } catch (Exception e) {
          throw new IllegalArgumentException("Deck exceeds 250 cards");
        }
        if (count < 1 || count > 250)
          throw new IllegalArgumentException("Deck must contain 1–250 cards");
        String name =
            match
                .group(2)
                .replaceFirst("\\s+\\([A-Za-z0-9]+\\)\\s+[A-Za-z0-9★-]+(?:\\s+\\*[^*]+\\*)?$", "")
                .trim();
        if (name.length() > 200) throw new IllegalArgumentException("Card name is too long");
        quantities.merge(name, count, Integer::sum);
        if (section.startsWith("commander")) suggested.add(name);
      }
    }
    int total = quantities.values().stream().mapToInt(Integer::intValue).sum();
    if (total < 1 || total > 250)
      throw new IllegalArgumentException("Deck must contain 1–250 cards");
    List<String> names = new ArrayList<>(quantities.keySet());
    List<Definition> resolved = catalog.resolve(names);
    if (resolved.size() != names.size())
      throw new IllegalArgumentException("Some card names could not be resolved");
    List<Definition> cards = new ArrayList<>();
    List<String> commanderIds = new ArrayList<>();
    for (int i = 0; i < names.size(); i++) {
      Definition def = resolved.get(i);
      cards.addAll(Collections.nCopies(quantities.get(names.get(i)), def));
      if (suggested.contains(names.get(i))) commanderIds.add(def.id());
    }
    return new Draft(
        cards, resolved.stream().distinct().toList(), commanderIds.stream().distinct().toList());
  }
}
