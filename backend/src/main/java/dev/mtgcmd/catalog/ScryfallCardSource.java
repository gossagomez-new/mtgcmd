package dev.mtgcmd.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.Game.Definition;
import dev.mtgcmd.game.Game.Face;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Translates collection protocol data into definitions and verified requested aliases. */
final class ScryfallCardSource {
  static final int MAX_BATCH_SIZE = 75;
  private static final URI COLLECTION_URI = URI.create("https://api.scryfall.com/cards/collection");
  private final JsonHttp http;
  private final ObjectMapper mapper;
  private final Clock clock;

  record MatchedCard(Definition definition, List<String> aliases) {}

  record LookupResult(boolean supportedEnvelope, Iterable<MatchedCard> cards) {}

  ScryfallCardSource(final JsonHttp http, final ObjectMapper mapper, final Clock clock) {
    this.http = http;
    this.mapper = mapper;
    this.clock = clock;
  }

  LookupResult lookupBatch(final List<String> names) {
    final JsonNode response = fetch(names);
    final boolean supported =
        response != null && response.isObject() && response.path("data").isArray();
    return new LookupResult(supported, () -> mappedCards(response, names));
  }

  private JsonNode fetch(final List<String> names) {
    final String request;
    try {
      request =
          mapper.writeValueAsString(
              Map.of(
                  "identifiers",
                  names.stream().map(name -> Map.of("name", collectionIdentifier(name))).toList()));
    } catch (JsonProcessingException e) {
      throw new CardServiceUnavailableException("Card catalog unavailable; try again later", e);
    }
    final String json = http.post(COLLECTION_URI, request);
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new InvalidCardServiceResponseException("Card catalog unavailable; try again later", e);
    }
  }

  private Iterator<MatchedCard> mappedCards(final JsonNode response, final List<String> names) {
    final Iterator<JsonNode> cards = response.path("data").iterator();
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return cards.hasNext();
      }

      @Override
      public MatchedCard next() {
        final Definition card = definition(cards.next(), clock.millis());
        final List<String> aliases = names.stream().filter(name -> matches(name, card)).toList();
        return new MatchedCard(card, aliases);
      }
    };
  }

  private static boolean matches(final String name, final Definition card) {
    final String key = CardNames.key(name);
    return key.equals(CardNames.key(card.name()))
        || card.faces().stream().anyMatch(face -> key.equals(CardNames.key(face.name())));
  }

  private static String collectionIdentifier(final String name) {
    // Collection identifiers accept individual face names, not combined printed names.
    final int separator = name.indexOf(" // ");
    return separator < 0 ? name : name.substring(0, separator).trim();
  }

  static Definition definition(final JsonNode node, final long observedAt) {
    final Map<String, String> legalities = new LinkedHashMap<>();
    node.path("legalities")
        .fields()
        .forEachRemaining(
            entry -> {
              if (entry.getValue().isTextual()) {
                legalities.put(entry.getKey(), entry.getValue().textValue());
              }
            });
    final List<Face> faces = new ArrayList<>();
    for (final JsonNode f : node.path("card_faces")) {
      faces.add(
          new Face(
              f.path("name").asText(),
              image(f),
              f.path("oracle_text").asText(),
              f.path("type_line").asText()));
    }
    final List<String> colors = new ArrayList<>();
    node.path("color_identity").forEach(n -> colors.add(n.asText()));
    String image = image(node);
    if (image.isEmpty() && !faces.isEmpty()) {
      image = faces.getFirst().image();
    }
    String text = node.path("oracle_text").asText();
    if (text.isEmpty()) {
      text = String.join("\n\n", faces.stream().map(Face::text).toList());
    }
    return new Definition(
        node.path("oracle_id").asText(node.path("id").asText()),
        node.path("name").asText(),
        image,
        text,
        node.path("type_line").asText(),
        colors,
        "legal".equals(node.path("legalities").path("commander").asText()),
        faces,
        legalities,
        observedAt);
  }

  private static String image(final JsonNode node) {
    final String value = node.path("image_uris").path("normal").asText("");
    try {
      final URI uri = URI.create(value);
      return "https".equals(uri.getScheme())
              && uri.getHost() != null
              && uri.getHost().endsWith(".scryfall.io")
          ? value
          : "";
    } catch (IllegalArgumentException e) {
      return "";
    }
  }
}
