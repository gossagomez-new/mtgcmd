package dev.mtgcmd.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.Game.Definition;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stores card definitions under canonical names and verified requested aliases. */
final class JdbcCardCache implements CardCache {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  JdbcCardCache(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public Optional<Definition> find(String name) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT definition::text FROM catalog WHERE name=?", String.class, CardNames.key(name));
    if (rows.isEmpty()) return Optional.empty();
    try {
      return Optional.of(mapper.readValue(rows.getFirst(), Definition.class));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid cached card", e);
    }
  }

  @Override
  public void store(String name, Definition definition) {
    try {
      jdbc.update(
          "INSERT INTO catalog(name,definition) VALUES (?,CAST(? AS jsonb)) ON CONFLICT(name) DO UPDATE SET definition=EXCLUDED.definition,updated_at=now()",
          CardNames.key(name),
          mapper.writeValueAsString(definition));
    } catch (JsonProcessingException | DataAccessException e) {
      throw new CardServiceUnavailableException("Card catalog unavailable; try again later", e);
    }
  }
}
