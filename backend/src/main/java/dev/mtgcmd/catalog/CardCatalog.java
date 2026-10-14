package dev.mtgcmd.catalog;

import com.fasterxml.jackson.databind.*;
import dev.mtgcmd.game.CardLegality;
import dev.mtgcmd.game.Game.*;
import dev.mtgcmd.game.GameFormat;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CardCatalog implements DeckImporter.Catalog {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final JsonHttp http;
  private final Clock clock;

  public CardCatalog(JdbcTemplate jdbc, ObjectMapper mapper, JsonHttp http) {
    this(jdbc, mapper, http, Clock.systemUTC());
  }

  public CardCatalog(JdbcTemplate jdbc, ObjectMapper mapper, JsonHttp http, Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.http = http;
    this.clock = clock;
  }

  public List<Definition> resolve(List<String> names) {
    return resolve(names, GameFormat.COMMANDER);
  }

  @Override
  public List<Definition> resolve(List<String> names, GameFormat format) {
    Map<String, Definition> found = new HashMap<>();
    Set<String> pending = new LinkedHashSet<>();
    for (String name : names) {
      String key = normalize(name);
      List<String> rows =
          jdbc.queryForList("SELECT definition::text FROM catalog WHERE name=?", String.class, key);
      if (rows.isEmpty()) pending.add(name);
      else {
        Definition def;
        try {
          def = mapper.readValue(rows.getFirst(), Definition.class);
        } catch (Exception e) {
          throw new IllegalStateException("Invalid cached card", e);
        }
        found.put(key, def);
        if (format == GameFormat.STANDARD && !CardLegality.fresh(def, "standard", clock))
          pending.add(name);
      }
    }
    List<String> requested = new ArrayList<>(pending);
    for (int offset = 0; offset < requested.size(); offset += 75) {
      List<String> chunk = requested.subList(offset, Math.min(offset + 75, requested.size()));
      JsonNode response;
      try {
        response = fetch(chunk);
        if (format == GameFormat.STANDARD
            && (response == null || !response.isObject() || !response.path("data").isArray()))
          throw new IllegalArgumentException("Card catalog returned an unsupported response");
      } catch (Exception e) {
        if (format == GameFormat.STANDARD
            && chunk.stream().allMatch(n -> found.containsKey(normalize(n)))) continue;
        if (e instanceof IllegalArgumentException invalid) throw invalid;
        throw new IllegalArgumentException("Card catalog unavailable; try again later");
      }
      try {
        for (JsonNode card : response.path("data")) {
          Definition def = definition(card, clock.millis());
          cache(def.name(), def);
          for (String name : chunk) {
            if (normalize(name).equals(normalize(def.name()))
                || def.faces().stream()
                    .anyMatch(f -> normalize(f.name()).equals(normalize(name)))) {
              found.put(normalize(name), def);
              cache(name, def);
            }
          }
        }
      } catch (Exception e) {
        if (e instanceof IllegalArgumentException invalid) throw invalid;
        throw new IllegalArgumentException("Card catalog unavailable; try again later");
      }
      for (String name : chunk) {
        if (!found.containsKey(normalize(name)))
          throw new IllegalArgumentException(
              "Unknown card: " + name + ". Check spelling or use the full printed name.");
      }
    }
    return names.stream().map(n -> found.get(normalize(n))).toList();
  }

  private JsonNode fetch(List<String> names) throws Exception {
    String request =
        mapper.writeValueAsString(
            Map.of(
                "identifiers",
                names.stream().map(n -> Map.of("name", collectionName(n))).toList()));
    return mapper.readTree(
        http.post(URI.create("https://api.scryfall.com/cards/collection"), request));
  }

  private void cache(String name, Definition def) throws Exception {
    jdbc.update(
        "INSERT INTO catalog(name,definition) VALUES (?,CAST(? AS jsonb)) ON CONFLICT(name) DO UPDATE SET definition=EXCLUDED.definition,updated_at=now()",
        normalize(name),
        mapper.writeValueAsString(def));
  }

  public static Definition definition(JsonNode c) {
    return definition(c, System.currentTimeMillis());
  }

  private static Definition definition(JsonNode c, long observedAt) {
    Map<String, String> legalities = new LinkedHashMap<>();
    c.path("legalities")
        .fields()
        .forEachRemaining(
            entry -> {
              if (entry.getValue().isTextual())
                legalities.put(entry.getKey(), entry.getValue().textValue());
            });
    List<Face> faces = new ArrayList<>();
    for (JsonNode f : c.path("card_faces"))
      faces.add(
          new Face(
              f.path("name").asText(),
              image(f),
              f.path("oracle_text").asText(),
              f.path("type_line").asText()));
    List<String> colors = new ArrayList<>();
    c.path("color_identity").forEach(n -> colors.add(n.asText()));
    String image = image(c);
    if (image.isEmpty() && !faces.isEmpty()) image = faces.getFirst().image();
    String text = c.path("oracle_text").asText();
    if (text.isEmpty()) text = String.join("\n\n", faces.stream().map(Face::text).toList());
    return new Definition(
        c.path("oracle_id").asText(c.path("id").asText()),
        c.path("name").asText(),
        image,
        text,
        c.path("type_line").asText(),
        colors,
        "legal".equals(c.path("legalities").path("commander").asText()),
        faces,
        legalities,
        observedAt);
  }

  private static String image(JsonNode c) {
    String value = c.path("image_uris").path("normal").asText("");
    try {
      URI uri = URI.create(value);
      return "https".equals(uri.getScheme())
              && uri.getHost() != null
              && uri.getHost().endsWith(".scryfall.io")
          ? value
          : "";
    } catch (Exception e) {
      return "";
    }
  }

  private static String collectionName(String name) {
    // Collection identifiers accept individual face names, not combined printed names.
    int separator = name.indexOf(" // ");
    return separator < 0 ? name : name.substring(0, separator).trim();
  }

  private static String normalize(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
}
