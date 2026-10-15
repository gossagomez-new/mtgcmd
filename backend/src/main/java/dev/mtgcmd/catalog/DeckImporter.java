package dev.mtgcmd.catalog;

import dev.mtgcmd.game.Game.Definition;
import dev.mtgcmd.game.GameFormat;
import java.net.URI;
import java.util.*;

public class DeckImporter {
  @FunctionalInterface
  public interface Catalog {
    List<Definition> resolve(List<String> names);

    default List<Definition> resolve(final List<String> names, GameFormat format) {
      return resolve(names);
    }
  }

  @FunctionalInterface
  public interface Fetcher {
    /** Returns provider data or throws a typed CardServiceException for operational failures. */
    String get(URI uri);
  }

  public record Draft(List<Definition> cards, List<Definition> choices, List<String> suggested) {}

  private final Catalog catalog;
  private final TextDeckParser textParser = new TextDeckParser();
  private final MoxfieldDeckSource moxfieldSource;

  public DeckImporter(final Catalog catalog, final Fetcher fetcher) {
    this.catalog = catalog;
    this.moxfieldSource = new MoxfieldDeckSource(fetcher);
  }

  public Draft preview(final String text, final String url) {
    return preview(text, url, GameFormat.COMMANDER);
  }

  public Draft preview(final String text, final String url, final GameFormat format) {
    final ParsedDeck deck =
        url != null && !url.isBlank() ? moxfieldSource.parse(url) : textParser.parse(text);
    deck.validateTotal();
    final List<Definition> resolved = catalog.resolve(deck.names(), format);
    if (resolved.size() != deck.entries().size()) {
      throw new IllegalArgumentException("Some card names could not be resolved");
    }
    return assembleDraft(deck, resolved);
  }

  private Draft assembleDraft(final ParsedDeck deck, final List<Definition> resolved) {
    final List<Definition> cards = new ArrayList<>();
    final List<String> commanderIds = new ArrayList<>();
    for (int i = 0; i < deck.entries().size(); i++) {
      final ParsedDeck.Entry entry = deck.entries().get(i);
      final Definition definition = resolved.get(i);
      cards.addAll(Collections.nCopies(entry.quantity(), definition));
      if (entry.commander()) {
        commanderIds.add(definition.id());
      }
    }
    return new Draft(
        cards, resolved.stream().distinct().toList(), commanderIds.stream().distinct().toList());
  }
}
