package dev.mtgcmd.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.CardLegality;
import dev.mtgcmd.game.Game.Definition;
import dev.mtgcmd.game.GameFormat;
import java.time.Clock;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/** Coordinates cached definitions, format-specific refresh, and per-batch recovery. */
public final class CardCatalog implements DeckImporter.Catalog {
  private static final Logger LOG = LoggerFactory.getLogger(CardCatalog.class);
  private final CardCache cache;
  private final ScryfallCardSource source;
  private final Clock clock;

  private record ResolutionPlan(Map<String, Definition> found, List<String> pending) {}

  public CardCatalog(final JdbcTemplate jdbc, final ObjectMapper mapper, final JsonHttp http) {
    this(jdbc, mapper, http, Clock.systemUTC());
  }

  public CardCatalog(
      final JdbcTemplate jdbc, final ObjectMapper mapper, final JsonHttp http, final Clock clock) {
    this(new JdbcCardCache(jdbc, mapper), new ScryfallCardSource(http, mapper, clock), clock);
  }

  CardCatalog(final CardCache cache, final ScryfallCardSource source, final Clock clock) {
    this.cache = cache;
    this.source = source;
    this.clock = clock;
  }

  public List<Definition> resolve(final List<String> names) {
    return resolve(names, GameFormat.COMMANDER);
  }

  @Override
  public List<Definition> resolve(final List<String> names, final GameFormat format) {
    final ResolutionPlan plan = planLookup(names, format);
    for (int offset = 0;
        offset < plan.pending().size();
        offset += ScryfallCardSource.MAX_BATCH_SIZE) {
      final List<String> batch =
          plan.pending()
              .subList(
                  offset,
                  Math.min(offset + ScryfallCardSource.MAX_BATCH_SIZE, plan.pending().size()));
      resolveBatch(batch, format, plan.found());
    }
    return names.stream().map(name -> plan.found().get(CardNames.key(name))).toList();
  }

  private ResolutionPlan planLookup(final List<String> names, final GameFormat format) {
    final Map<String, Definition> found = new HashMap<>();
    final Set<String> pending = new LinkedHashSet<>();
    for (final String name : names) {
      final Optional<Definition> cached = cache.find(name);
      cached.ifPresent(definition -> found.put(CardNames.key(name), definition));
      if (needsLookup(cached, format)) {
        pending.add(name);
      }
    }
    return new ResolutionPlan(found, new ArrayList<>(pending));
  }

  private boolean needsLookup(final Optional<Definition> cached, final GameFormat format) {
    return cached.isEmpty()
        || (format == GameFormat.STANDARD && !CardLegality.fresh(cached.get(), "standard", clock));
  }

  private void resolveBatch(
      final List<String> batch, final GameFormat format, final Map<String, Definition> found) {
    final ScryfallCardSource.LookupResult result;
    try {
      result = fetchBatch(batch, format);
    } catch (CardServiceInterruptedException e) {
      throw e;
    } catch (CardServiceException e) {
      if (!canUseCachedBatch(batch, format, found)) {
        throw e;
      }
      LOG.warn("Catalog refresh failed; using cached definitions", e);
      return;
    }
    persistAndMerge(result, found);
    requireResolved(batch, found);
  }

  private ScryfallCardSource.LookupResult fetchBatch(
      final List<String> batch, final GameFormat format) {
    final ScryfallCardSource.LookupResult result = source.lookupBatch(batch);
    if (format == GameFormat.STANDARD && !result.supportedEnvelope()) {
      throw new InvalidCardServiceResponseException(
          "Card catalog returned an unsupported response", null);
    }
    return result;
  }

  private boolean canUseCachedBatch(
      final List<String> batch, final GameFormat format, final Map<String, Definition> found) {
    return format == GameFormat.STANDARD
        && batch.stream().allMatch(name -> found.containsKey(CardNames.key(name)));
  }

  private void persistAndMerge(
      final ScryfallCardSource.LookupResult result, final Map<String, Definition> found) {
    for (final ScryfallCardSource.MatchedCard card : result.cards()) {
      cache.store(card.definition().name(), card.definition());
      for (final String alias : card.aliases()) {
        found.put(CardNames.key(alias), card.definition());
        cache.store(alias, card.definition());
      }
    }
  }

  private void requireResolved(final List<String> batch, final Map<String, Definition> found) {
    for (final String name : batch) {
      if (!found.containsKey(CardNames.key(name))) {
        throw new IllegalArgumentException(
            "Unknown card: " + name + ". Check spelling or use the full printed name.");
      }
    }
  }

  /** Compatibility entry point for provider definition mapping. */
  public static Definition definition(final JsonNode card) {
    return ScryfallCardSource.definition(card, System.currentTimeMillis());
  }
}
