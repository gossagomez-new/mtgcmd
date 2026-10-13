package dev.mtgcmd.catalog;

import dev.mtgcmd.game.Game.Definition;
import java.net.URI;
import java.util.*;

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
  private final TextDeckParser textParser = new TextDeckParser();
  private final MoxfieldDeckSource moxfieldSource;

  public DeckImporter(Catalog catalog, Fetcher fetcher) {
    this.catalog = catalog;
    this.moxfieldSource = new MoxfieldDeckSource(fetcher);
  }

  public Draft preview(String text, String url) {
    ParsedDeck deck =
        url != null && !url.isBlank() ? moxfieldSource.parse(url) : textParser.parse(text);
    deck.validateTotal();
    List<Definition> resolved = catalog.resolve(deck.names());
    if (resolved.size() != deck.entries().size())
      throw new IllegalArgumentException("Some card names could not be resolved");
    return assembleDraft(deck, resolved);
  }

  private Draft assembleDraft(ParsedDeck deck, List<Definition> resolved) {
    List<Definition> cards = new ArrayList<>();
    List<String> commanderIds = new ArrayList<>();
    for (int i = 0; i < deck.entries().size(); i++) {
      ParsedDeck.Entry entry = deck.entries().get(i);
      Definition definition = resolved.get(i);
      cards.addAll(Collections.nCopies(entry.quantity(), definition));
      if (entry.commander()) commanderIds.add(definition.id());
    }
    return new Draft(
        cards, resolved.stream().distinct().toList(), commanderIds.stream().distinct().toList());
  }
}
