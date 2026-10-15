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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DeckDraftRepositoryTest {
  private static final String EXPIRED_AGE = "25 hours";

  private static final String DRAFT_ID = "draft";

  private static final String OLD_DRAFT_ID = "old";

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
    if (postgres != null) {
      postgres.close();
    }
  }

  @BeforeEach
  void setup() {
    jdbc.update("DELETE FROM deck_drafts");
    jdbc.update("DELETE FROM guests");
    guest = UUID.randomUUID().toString();
    otherGuest = UUID.randomUUID().toString();
    for (final String hash : List.of(guest, otherGuest)) {
      jdbc.update("INSERT INTO guests(token_hash,csrf_token) VALUES (?,?)", hash, "csrf");
    }
    drafts = new DeckDraftRepository(jdbc, mapper);
  }

  private void seed(final String identifier, final String owner, final String age)
      throws Exception {
    jdbc.update(
        "INSERT INTO deck_drafts(id,guest_hash,cards,created_at) VALUES (?,?,CAST(? AS jsonb),now()-CAST(? AS interval))",
        identifier,
        owner,
        mapper.writeValueAsString(List.of(card)),
        age);
  }

  @Test
  void roundTripRetainsCompleteDefinitionsAndSupportsReuse() throws Exception {
    drafts.save(guest, DRAFT_ID, List.of(card, card));
    assertThat(drafts.requireAvailable(guest, DRAFT_ID)).containsExactly(card, card);
    assertThat(drafts.requireAvailable(guest, DRAFT_ID)).containsExactly(card, card);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM deck_drafts WHERE id='draft'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void anotherGuestCannotReadDraftAndMissingDraftKeepsError() throws Exception {
    drafts.save(guest, DRAFT_ID, List.of(card));
    for (final String id : List.of(DRAFT_ID, "absent")) {
      assertThatThrownBy(() -> drafts.requireAvailable(otherGuest, id))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Deck preview expired; preview again");
    }
  }

  @Test
  void exactDayBoundaryIsUnavailableButNotPurged() {
    final TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    transaction.executeWithoutResult(
        status -> {
          try {
            seed(OLD_DRAFT_ID, guest, EXPIRED_AGE);
            seed("boundary", guest, "1 day");
            seed("fresh", guest, "23 hours");
            seed("foreign", otherGuest, EXPIRED_AGE);
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
    seed(OLD_DRAFT_ID, guest, EXPIRED_AGE);
    assertThatThrownBy(() -> drafts.requireAvailable(guest, OLD_DRAFT_ID))
        .hasMessage("Deck preview expired; preview again");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM deck_drafts", Integer.class)).isEqualTo(1);
  }

  @Test
  void failedInsertDoesNotRollBackPriorPurge() throws Exception {
    seed(OLD_DRAFT_ID, guest, EXPIRED_AGE);
    seed("existing", guest, "0 hours");
    assertThatThrownBy(() -> drafts.save(guest, "existing", List.of(card)))
        .isInstanceOf(DuplicateKeyException.class);
    assertThat(jdbc.queryForList("SELECT id FROM deck_drafts", String.class))
        .containsExactly("existing");
  }

  @Test
  void serializationFailureRetainsPartialPurgeAndOriginalException() throws Exception {
    seed(OLD_DRAFT_ID, guest, EXPIRED_AGE);
    final ObjectMapper failingMapper = spy(new ObjectMapper());
    final JsonProcessingException failure = new JsonProcessingException("broken") {};
    doThrow(failure).when(failingMapper).writeValueAsString(any());
    final DeckDraftRepository failing = new DeckDraftRepository(jdbc, failingMapper);
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
    final JdbcTemplate broken = mock(JdbcTemplate.class);
    final DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("offline");
    when(broken.queryForList(anyString(), eq(String.class), eq(DRAFT_ID), eq(guest)))
        .thenThrow(failure);
    assertThatThrownBy(
            () -> new DeckDraftRepository(broken, mapper).requireAvailable(guest, DRAFT_ID))
        .isSameAs(failure);
  }
}
