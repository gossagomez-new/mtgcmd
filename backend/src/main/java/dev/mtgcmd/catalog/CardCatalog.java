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

  public CardCatalog(JdbcTemplate jdbc, ObjectMapper mapper, JsonHttp http) {
    this(jdbc, mapper, http, Clock.systemUTC());
  }

  public CardCatalog(JdbcTemplate jdbc, ObjectMapper mapper, JsonHttp http, Clock clock) {
    this(new JdbcCardCache(jdbc, mapper), new ScryfallCardSource(http, mapper, clock), clock);
  }

  CardCatalog(CardCache cache, ScryfallCardSource source, Clock clock) {
    this.cache = cache;
    this.source = source;
    this.clock = clock;
  }

  public List<Definition> resolve(List<String> names) {
    return resolve(names, GameFormat.COMMANDER);
  }

  @Override
  public List<Definition> resolve(List<String> names, GameFormat format) {
    ResolutionPlan plan = planLookup(names, format);
    for (int offset = 0;
        offset < plan.pending().size();
        offset += ScryfallCardSource.MAX_BATCH_SIZE) {
      List<String> batch =
          plan.pending()
              .subList(
                  offset,
                  Math.min(offset + ScryfallCardSource.MAX_BATCH_SIZE, plan.pending().size()));
      resolveBatch(batch, format, plan.found());
    }
    return names.stream().map(name -> plan.found().get(CardNames.key(name))).toList();
  }

  private ResolutionPlan planLookup(List<String> names, GameFormat format) {
    Map<String, Definition> found = new HashMap<>();
    Set<String> pending = new LinkedHashSet<>();
    for (String name : names) {
      Optional<Definition> cached = cache.find(name);
      cached.ifPresent(definition -> found.put(CardNames.key(name), definition));
      if (needsLookup(cached, format)) pending.add(name);
    }
    return new ResolutionPlan(found, new ArrayList<>(pending));
  }

  private boolean needsLookup(Optional<Definition> cached, GameFormat format) {
    return cached.isEmpty()
        || (format == GameFormat.STANDARD && !CardLegality.fresh(cached.get(), "standard", clock));
  }

  private void resolveBatch(List<String> batch, GameFormat format, Map<String, Definition> found) {
    ScryfallCardSource.LookupResult result;
    try {
      result = fetchBatch(batch, format);
    } catch (CardServiceInterruptedException e) {
      throw e;
    } catch (CardServiceException e) {
      if (!canUseCachedBatch(batch, format, found)) throw e;
      LOG.warn("Catalog refresh failed; using cached definitions", e);
      return;
    }
    persistAndMerge(result, found);
    requireResolved(batch, found);
  }

  private ScryfallCardSource.LookupResult fetchBatch(List<String> batch, GameFormat format) {
    ScryfallCardSource.LookupResult result = source.lookupBatch(batch);
    if (format == GameFormat.STANDARD && !result.supportedEnvelope())
      throw new InvalidCardServiceResponseException(
          "Card catalog returned an unsupported response", null);
    return result;
  }

  private boolean canUseCachedBatch(
      List<String> batch, GameFormat format, Map<String, Definition> found) {
    return format == GameFormat.STANDARD
        && batch.stream().allMatch(name -> found.containsKey(CardNames.key(name)));
  }

  private void persistAndMerge(
      ScryfallCardSource.LookupResult result, Map<String, Definition> found) {
    for (ScryfallCardSource.MatchedCard card : result.cards()) {
      cache.store(card.definition().name(), card.definition());
      for (String alias : card.aliases()) {
        found.put(CardNames.key(alias), card.definition());
        cache.store(alias, card.definition());
      }
    }
  }

  private void requireResolved(List<String> batch, Map<String, Definition> found) {
    for (String name : batch) {
      if (!found.containsKey(CardNames.key(name)))
        throw new IllegalArgumentException(
            "Unknown card: " + name + ". Check spelling or use the full printed name.");
    }
  }

  /** Compatibility entry point for provider definition mapping. */
  public static Definition definition(JsonNode card) {
    return ScryfallCardSource.definition(card, System.currentTimeMillis());
  }
}
