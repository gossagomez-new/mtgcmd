package dev.mtgcmd.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.Game;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DeckDraftRepositoryTest {
  private static EmbeddedPostgres postgres;
  private static JdbcTemplate jdbc;
  private final ObjectMapper mapper = new ObjectMapper();
  private DeckDraftRepository drafts;
  private String guest;
  private String otherGuest;
  private final Game.Definition card =
      new Game.Definition(
          "card",
          "Card",
          "image",
          "text",
          "Creature",
          List.of("U"),
          true,
          List.of(new Game.Face("Front", "image", "text", "Creature")),
          Map.of("standard", "legal"),
          123L);

  @BeforeAll
  static void database() throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(0).start();
    Flyway.configure().dataSource(postgres.getPostgresDatabase()).load().migrate();
    jdbc = new JdbcTemplate(postgres.getPostgresDatabase());
  }

  @AfterAll
  static void close() throws Exception {
    if (postgres != null) postgres.close();
  }

  @BeforeEach
  void setup() {
    jdbc.update("DELETE FROM deck_drafts");
    jdbc.update("DELETE FROM guests");
    guest = UUID.randomUUID().toString();
    otherGuest = UUID.randomUUID().toString();
    for (String hash : List.of(guest, otherGuest))
      jdbc.update("INSERT INTO guests(token_hash,csrf_token) VALUES (?,?)", hash, "csrf");
    drafts = new DeckDraftRepository(jdbc, mapper);
  }

  private void seed(String id, String owner, String age) throws Exception {
    jdbc.update(
        "INSERT INTO deck_drafts(id,guest_hash,cards,created_at) VALUES (?,?,CAST(? AS jsonb),now()-CAST(? AS interval))",
        id,
        owner,
        mapper.writeValueAsString(List.of(card)),
        age);
  }

  @Test
  void roundTripRetainsCompleteDefinitionsAndSupportsReuse() throws Exception {
    drafts.save(guest, "draft", List.of(card, card));
    assertThat(drafts.requireAvailable(guest, "draft")).containsExactly(card, card);
    assertThat(drafts.requireAvailable(guest, "draft")).containsExactly(card, card);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM deck_drafts WHERE id='draft'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void anotherGuestCannotReadDraftAndMissingDraftKeepsError() throws Exception {
    drafts.save(guest, "draft", List.of(card));
    for (String id : List.of("draft", "absent")) {
      assertThatThrownBy(() -> drafts.requireAvailable(otherGuest, id))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Deck preview expired; preview again");
    }
  }

  @Test
  void exactDayBoundaryIsUnavailableButNotPurged() {
    var transaction =
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    transaction.executeWithoutResult(
        status -> {
          try {
            seed("old", guest, "25 hours");
            seed("boundary", guest, "1 day");
            seed("fresh", guest, "23 hours");
            seed("foreign", otherGuest, "25 hours");
            drafts.save(guest, "new", List.of(card));
            assertThat(jdbc.queryForList("SELECT id FROM deck_drafts ORDER BY id", String.class))
                .containsExactly("boundary", "foreign", "fresh", "new");
            assertThatThrownBy(() -> drafts.requireAvailable(guest, "boundary"))
                .hasMessage("Deck preview expired; preview again");
            assertThat(drafts.requireAvailable(guest, "fresh")).containsExactly(card);
          } catch (JsonProcessingException exception) {
            throw new AssertionError(exception);
          } catch (Exception exception) {
            throw new AssertionError(exception);
          }
        });
  }

  @Test
  void expiredLookupDoesNotDeleteRows() throws Exception {
    seed("old", guest, "25 hours");
    assertThatThrownBy(() -> drafts.requireAvailable(guest, "old"))
        .hasMessage("Deck preview expired; preview again");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM deck_drafts", Integer.class)).isEqualTo(1);
  }

  @Test
  void failedInsertDoesNotRollBackPriorPurge() throws Exception {
    seed("old", guest, "25 hours");
    seed("existing", guest, "0 hours");
    assertThatThrownBy(() -> drafts.save(guest, "existing", List.of(card)))
        .isInstanceOf(DuplicateKeyException.class);
    assertThat(jdbc.queryForList("SELECT id FROM deck_drafts", String.class))
        .containsExactly("existing");
  }

  @Test
  void serializationFailureRetainsPartialPurgeAndOriginalException() throws Exception {
    seed("old", guest, "25 hours");
    var failingMapper = spy(new ObjectMapper());
    var failure = new JsonProcessingException("broken") {};
    doThrow(failure).when(failingMapper).writeValueAsString(any());
    var failing = new DeckDraftRepository(jdbc, failingMapper);
    assertThatThrownBy(() -> failing.save(guest, "new", List.of(card))).isSameAs(failure);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM deck_drafts", Integer.class)).isZero();
  }

  @Test
  void malformedStoredShapePropagatesJsonFailure() {
    jdbc.update(
        "INSERT INTO deck_drafts(id,guest_hash,cards) VALUES (?,?,CAST(? AS jsonb))",
        "bad",
        guest,
        "{}");
    assertThatThrownBy(() -> drafts.requireAvailable(guest, "bad"))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void sqlFailurePropagatesWithoutTranslation() throws Exception {
    var broken = mock(JdbcTemplate.class);
    var failure = new org.springframework.dao.DataAccessResourceFailureException("offline");
    when(broken.queryForList(anyString(), eq(String.class), eq("draft"), eq(guest)))
        .thenThrow(failure);
    assertThatThrownBy(
            () -> new DeckDraftRepository(broken, mapper).requireAvailable(guest, "draft"))
        .isSameAs(failure);
  }
}
