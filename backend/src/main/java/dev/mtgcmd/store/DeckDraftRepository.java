package dev.mtgcmd.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.Game;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Guest-scoped draft SQL and JSON; operations retain their independent transaction behavior. */
public final class DeckDraftRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public DeckDraftRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void save(String guest, String draftId, List<Game.Definition> cards)
      throws JsonProcessingException {
    jdbc.update(
        "DELETE FROM deck_drafts WHERE guest_hash=? AND created_at<now()-interval '1 day'", guest);
    jdbc.update(
        "INSERT INTO deck_drafts(id,guest_hash,cards) VALUES (?,?,CAST(? AS jsonb))",
        draftId,
        guest,
        mapper.writeValueAsString(cards));
  }

  public List<Game.Definition> requireAvailable(String guest, String draftId)
      throws JsonProcessingException {
    var rows =
        jdbc.queryForList(
            "SELECT cards::text FROM deck_drafts WHERE id=? AND guest_hash=? AND created_at>now()-interval '1 day'",
            String.class,
            draftId,
            guest);
    if (rows.isEmpty()) throw new IllegalArgumentException("Deck preview expired; preview again");
    return mapper.readValue(rows.getFirst(), new TypeReference<>() {});
  }
}
