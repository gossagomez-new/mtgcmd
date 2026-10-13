package dev.mtgcmd.catalog;

import com.fasterxml.jackson.databind.*;
import dev.mtgcmd.game.Game.*;
import java.net.URI;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CardCatalog implements DeckImporter.Catalog {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final JsonHttp http;

  public CardCatalog(JdbcTemplate jdbc, ObjectMapper mapper, JsonHttp http) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.http = http;
  }

  public List<Definition> resolve(List<String> names) {
    Map<String, Definition> found = new HashMap<>();
    List<String> missing = new ArrayList<>();
    for (String name : names) {
      String key = normalize(name);
      List<String> rows =
          jdbc.queryForList("SELECT definition::text FROM catalog WHERE name=?", String.class, key);
      if (rows.isEmpty()) missing.add(name);
      else {
        try {
          found.put(key, mapper.readValue(rows.getFirst(), Definition.class));
        } catch (Exception e) {
          throw new IllegalStateException("Invalid cached card", e);
        }
      }
    }
    for (int offset = 0; offset < missing.size(); offset += 75) {
      List<String> chunk = missing.subList(offset, Math.min(offset + 75, missing.size()));
      try {
        String request =
            mapper.writeValueAsString(
                Map.of("identifiers", chunk.stream().map(n -> Map.of("name", n)).toList()));
        JsonNode response =
            mapper.readTree(
                http.post(URI.create("https://api.scryfall.com/cards/collection"), request));
        for (JsonNode card : response.path("data")) {
          Definition def = definition(card);
          cache(def.name(), def);
          for (String name : chunk)
            if (normalize(name).equals(normalize(def.name()))
                || def.faces().stream()
                    .anyMatch(f -> normalize(f.name()).equals(normalize(name)))) {
              found.put(normalize(name), def);
              cache(name, def);
            }
        }
        for (String name : chunk)
          if (!found.containsKey(normalize(name)))
            throw new IllegalArgumentException(
                "Unknown card: " + name + ". Check spelling or use the full printed name.");
      } catch (IllegalArgumentException e) {
        throw e;
      } catch (Exception e) {
        throw new IllegalArgumentException("Card catalog unavailable; try again later");
      }
    }
    return names.stream().map(n -> found.get(normalize(n))).toList();
  }

  private void cache(String name, Definition def) throws Exception {
    jdbc.update(
        "INSERT INTO catalog(name,definition) VALUES (?,CAST(? AS jsonb)) ON CONFLICT(name) DO UPDATE SET definition=EXCLUDED.definition,updated_at=now()",
        normalize(name),
        mapper.writeValueAsString(def));
  }

  public static Definition definition(JsonNode c) {
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
        faces);
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

  private static String normalize(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
}
