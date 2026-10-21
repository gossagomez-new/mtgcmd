package dev.mtgcmd.store;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.*;
import dev.mtgcmd.game.Game.Command;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class RoomStoreTest {
  private static final String DRAW_COMMAND = "DRAW";

  private static final String COUNT_FIELD = "count";

  private static final String FORMAT_FIELD = "format";

  static EmbeddedPostgres postgres;
  static JdbcTemplate jdbc;
  static TransactionTemplate transactions;
  RoomStore store;
  List<String> guests;

  @BeforeAll
  static void database() throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(0).start();
    final DataSource dataSource = postgres.getPostgresDatabase();
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @AfterAll
  static void close() throws Exception {
    if (postgres != null) {
      postgres.close();
    }
  }

  @BeforeEach
  void setup() {
    store = new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine());
    guests = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final String guest = UUID.randomUUID().toString();
      guests.add(guest);
      jdbc.update("INSERT INTO guests(token_hash,csrf_token) VALUES (?,?)", guest, "csrf");
    }
  }

  Map<String, Object> game() {
    return game(GameFormat.COMMANDER);
  }

  Map<String, Object> game(final GameFormat format) {
    Map<String, Object> view = store.create(guests.getFirst(), "A", format);
    final String identifier = (String) view.get("id");
    for (int i = 1; i < format.seatCapacity(); i++) {
      store.join(guests.get(i), identifier, "Player " + i);
    }
    final Game.Definition card =
        new Game.Definition(
            "island", "Island", "", "", "Basic Land — Island", List.of("U"), true, List.of());
    for (int i = 0; i < format.seatCapacity(); i++) {
      view = store.view(guests.get(i), identifier);
      store.load(
          guests.get(i),
          identifier,
          UUID.randomUUID().toString(),
          rev(view),
          Collections.nCopies(format == GameFormat.STANDARD ? 60 : 20, card),
          format.usesCommanders() ? List.of("island") : List.of());
      view = store.view(guests.get(i), identifier);
      store.execute(
          guests.get(i), identifier, command(rev(view), "READY", Map.of("acceptWarnings", true)));
    }
    view = store.view(guests.getFirst(), identifier);
    return store.execute(guests.getFirst(), identifier, command(rev(view), "START", Map.of()));
  }

  static long rev(final Map<String, Object> view) {
    return ((Number) view.get("revision")).longValue();
  }

  static Game.Command command(final long rev, final String type, final Map<String, Object> data) {
    return new Game.Command(UUID.randomUUID().toString(), rev, type, data);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void turnReturnSurvivesRecoveryAndDuplicateRetriesDoNotConsumeHistory(final GameFormat format)
      throws java.io.IOException {
    final Map<String, Object> started = turnRoom(format);
    final String identifier = (String) started.get("id");
    store.execute(guests.getFirst(), identifier, command(rev(started), "END_TURN", Map.of()));
    final RoomStore restarted =
        new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine());
    final Map<String, Object> eligible = restarted.view(guests.get(1), identifier);
    assertThat(eligible).containsEntry("canGiveBackTurn", true).containsEntry("turnVisit", 1L);
    final Command giveBack = command(rev(eligible), "GIVE_BACK_TURN", Map.of());
    final Map<String, Object> returned = restarted.execute(guests.get(1), identifier, giveBack);
    assertThat(returned)
        .containsEntry("active", 0)
        .containsEntry("turn", 1)
        .containsEntry("turnVisit", 2L);
    assertThat(restarted.execute(guests.get(1), identifier, giveBack)).isEqualTo(returned);
    final Map<String, Object> advanced =
        restarted.execute(guests.getFirst(), identifier, command(rev(returned), "PHASE", Map.of()));
    final Map<String, Object> retry = restarted.execute(guests.get(1), identifier, giveBack);
    assertThat(rev(retry)).isEqualTo(rev(advanced));
    assertThat(retry).containsEntry("phase", "Upkeep").containsEntry("turnVisit", 2L);
    assertThatThrownBy(
            () ->
                restarted.execute(
                    guests.get(1),
                    identifier,
                    new Command(
                        giveBack.id(),
                        giveBack.revision(),
                        "GIVE_BACK_TURN",
                        Map.of("extra", true))))
        .hasMessageContaining("reused");
    assertThatThrownBy(
            () ->
                restarted.execute(
                    guests.getFirst(),
                    identifier,
                    command(giveBack.revision(), "GIVE_BACK_TURN", Map.of())))
        .isInstanceOf(RoomStore.Conflict.class);
    final String saved =
        jdbc.queryForObject(
            "SELECT snapshot::text FROM rooms WHERE id=?", String.class, identifier);
    assertThat(new ObjectMapper().readTree(saved).path("turnTracking").path("history").size())
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commands WHERE room_id=? AND command_id=?",
                Integer.class,
                identifier,
                giveBack.id()))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void failedTurnReturnPersistenceRollsBackHistoryAndCanBeRetried(final GameFormat format)
      throws java.io.IOException {
    final Map<String, Object> started = turnRoom(format);
    final String identifier = (String) started.get("id");
    store.execute(guests.getFirst(), identifier, command(rev(started), "END_TURN", Map.of()));
    final Map<String, Object> before = store.view(guests.get(1), identifier);
    final Command giveBack = command(rev(before), "GIVE_BACK_TURN", Map.of());
    jdbc.execute(
        "CREATE FUNCTION reject_return_action() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'return persistence failure'; END $$");
    jdbc.execute(
        "CREATE TRIGGER reject_return BEFORE INSERT ON actions FOR EACH ROW EXECUTE FUNCTION reject_return_action()");
    try {
      assertThatThrownBy(() -> store.execute(guests.get(1), identifier, giveBack))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(store.view(guests.get(1), identifier)).isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM commands WHERE room_id=? AND command_id=?",
                  Integer.class,
                  identifier,
                  giveBack.id()))
          .isZero();
      final String saved =
          jdbc.queryForObject(
              "SELECT snapshot::text FROM rooms WHERE id=?", String.class, identifier);
      assertThat(new ObjectMapper().readTree(saved).path("turnTracking").path("history").size())
          .isEqualTo(1);
    } finally {
      jdbc.execute("DROP TRIGGER reject_return ON actions");
      jdbc.execute("DROP FUNCTION reject_return_action()");
    }
    assertThat(store.execute(guests.get(1), identifier, giveBack))
        .containsEntry("active", 0)
        .containsEntry("turnVisit", 2L);
  }

  private Map<String, Object> turnRoom(final GameFormat format) {
    Map<String, Object> view = store.create(guests.getFirst(), "A", format);
    final String identifier = (String) view.get("id");
    for (int seat = 1; seat < format.seatCapacity(); seat++) {
      store.join(guests.get(seat), identifier, "Player " + seat);
    }
    final Game.Definition card =
        new Game.Definition(
            "island", "Island", "", "", "Basic Land — Island", List.of("U"), true, List.of());
    for (int seat = 0; seat < format.seatCapacity(); seat++) {
      view = store.view(guests.get(seat), identifier);
      store.load(
          guests.get(seat),
          identifier,
          UUID.randomUUID().toString(),
          rev(view),
          Collections.nCopies(format == GameFormat.STANDARD ? 60 : 20, card),
          format.usesCommanders() ? List.of("island") : List.of());
      view = store.view(guests.get(seat), identifier);
      store.execute(
          guests.get(seat),
          identifier,
          command(rev(view), "READY", Map.of("acceptWarnings", true)));
    }
    view = store.view(guests.getFirst(), identifier);
    return store.execute(guests.getFirst(), identifier, command(rev(view), "START", Map.of()));
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void boundedInspectionAndScrySurviveRecoveryAndDuplicateRetries(final GameFormat format) {
    final Map<String, Object> started = game(format);
    final String id = (String) started.get("id");
    final Command open = command(rev(started), "SCRY", Map.of("count", 3));
    final Map<String, Object> opened = store.execute(guests.getFirst(), id, open);
    final RoomStore recovered =
        new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine());
    assertThat(recovered.view(guests.getFirst(), id)).isEqualTo(opened);
    assertThat(recovered.execute(guests.getFirst(), id, open)).isEqualTo(opened);
    final com.fasterxml.jackson.databind.JsonNode session =
        new ObjectMapper().valueToTree(opened.get("libraryInspection"));
    final List<String> cards = new ArrayList<>();
    session.path("cardIds").forEach(value -> cards.add(value.asText()));
    for (final String cardId : cards)
      assertThat(recovered.view(guests.get(1), id).toString()).doesNotContain(cardId);
    final Command apply =
        command(
            rev(opened),
            "APPLY_SCRY",
            Map.of(
                "inspectionId",
                session.path("id").asText(),
                "top",
                List.of(cards.get(2)),
                "bottom",
                List.of(cards.get(1), cards.get(0))));
    final Map<String, Object> result = recovered.execute(guests.getFirst(), id, apply);
    assertThat(result.get("libraryInspection")).isNull();
    assertThat(rev(result)).isEqualTo(rev(opened) + 1);
    assertThat(recovered.execute(guests.getFirst(), id, apply)).isEqualTo(result);
    assertThat(recovered.execute(guests.getFirst(), id, open)).isEqualTo(result);
    assertThat(
            new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine())
                .view(guests.getFirst(), id))
        .isEqualTo(result);
    assertThatThrownBy(
            () ->
                recovered.execute(
                    guests.getFirst(),
                    id,
                    new Command(apply.id(), apply.revision(), "CLOSE_INSPECTION", apply.data())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                recovered.execute(
                    guests.getFirst(), id, command(rev(opened), "APPLY_SCRY", apply.data())))
        .isInstanceOf(RoomStore.Conflict.class);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void failedScryPersistenceRollsBackOrderSessionAndCommandRecord(final GameFormat format) {
    final Map<String, Object> started = game(format);
    final String id = (String) started.get("id");
    final Map<String, Object> opened =
        store.execute(guests.getFirst(), id, command(rev(started), "SCRY", Map.of("count", 1)));
    final com.fasterxml.jackson.databind.JsonNode session =
        new ObjectMapper().valueToTree(opened.get("libraryInspection"));
    final Command apply =
        command(
            rev(opened),
            "APPLY_SCRY",
            Map.of(
                "inspectionId",
                session.path("id").asText(),
                "top",
                List.of(),
                "bottom",
                List.of(session.path("cardIds").get(0).asText())));
    final String saved =
        jdbc.queryForObject("SELECT snapshot::text FROM rooms WHERE id=?", String.class, id);
    jdbc.execute(
        "CREATE FUNCTION reject_scry_action() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'scry persistence failure'; END $$");
    jdbc.execute(
        "CREATE TRIGGER reject_scry BEFORE INSERT ON actions FOR EACH ROW EXECUTE FUNCTION reject_scry_action()");
    try {
      assertThatThrownBy(() -> store.execute(guests.getFirst(), id, apply))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(store.view(guests.getFirst(), id)).isEqualTo(opened);
      assertThat(
              jdbc.queryForObject("SELECT snapshot::text FROM rooms WHERE id=?", String.class, id))
          .isEqualTo(saved);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM commands WHERE room_id=? AND command_id=?",
                  Integer.class,
                  id,
                  apply.id()))
          .isZero();
    } finally {
      jdbc.execute("DROP TRIGGER reject_scry ON actions");
      jdbc.execute("DROP FUNCTION reject_scry_action()");
    }
    assertThat(store.execute(guests.getFirst(), id, apply).get("libraryInspection")).isNull();
  }

  @Test
  void endTurnIsAtomicIdempotentAndRejectsTheNonactiveHost() {
    final Map<String, Object> view = game();
    final String identifier = (String) view.get("id");
    final Command end = command(rev(view), "END_TURN", Map.of());
    final Map<String, Object> result = store.execute(guests.getFirst(), identifier, end);
    assertThat(result)
        .containsEntry("active", 1)
        .containsEntry("turn", 2)
        .containsEntry("phase", "Untap");
    assertThat(rev(result)).isEqualTo(rev(view) + 1);
    assertThat(store.execute(guests.getFirst(), identifier, end)).isEqualTo(result);
    assertThatThrownBy(
            () ->
                store.execute(
                    guests.getFirst(), identifier, command(rev(result), "PHASE", Map.of())))
        .hasMessage("Only the active player can advance turns");
    assertThat(
            new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine())
                .view(guests.getFirst(), identifier))
        .isEqualTo(result);
  }

  @Test
  void selectedTapPersistsAndRetriesDoNotRepeatTheMutation() {
    Map<String, Object> view = game();
    final String identifier = (String) view.get("id");
    final ObjectMapper mapper = new ObjectMapper();
    final String cardId = mapper.valueToTree(view).path("cards").get(0).path("id").asText();
    view =
        store.execute(
            guests.getFirst(),
            identifier,
            command(rev(view), "MOVE", Map.of("cardId", cardId, "zone", "battlefield")));
    final Command batch =
        command(rev(view), "TAP_SELECTED", Map.of("cardIds", List.of(cardId), "tapped", true));
    final Map<String, Object> result = store.execute(guests.getFirst(), identifier, batch);
    assertThat(rev(result)).isEqualTo(rev(view) + 1);
    assertThat(store.execute(guests.getFirst(), identifier, batch)).isEqualTo(result);
    assertThat(
            new RoomStore(jdbc, transactions, mapper, new GameEngine())
                .view(guests.getFirst(), identifier))
        .isEqualTo(result);
    assertThatThrownBy(
            () ->
                store.execute(
                    guests.getFirst(),
                    identifier,
                    command(
                        rev(result),
                        "TAP_SELECTED",
                        Map.of("cardIds", List.of(cardId, "missing"), "tapped", false))))
        .hasMessage("Card unavailable");
    assertThat(store.view(guests.getFirst(), identifier)).isEqualTo(result);
  }

  @Test
  void duplicateCommandsApplyOnceAndNewStoreRecoversState() {
    final Map<String, Object> view = game();
    final String identifier = (String) view.get("id");
    final Command cmd = command(rev(view), DRAW_COMMAND, Map.of(COUNT_FIELD, 1));
    final Map<String, Object> result = store.execute(guests.getFirst(), identifier, cmd);
    assertThat(store.execute(guests.getFirst(), identifier, cmd)).isEqualTo(result);
    final RoomStore restarted =
        new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine());
    assertThat(restarted.view(guests.getFirst(), identifier)).isEqualTo(result);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commands WHERE room_id=? AND command_id=?",
                Integer.class,
                identifier,
                cmd.id()))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                store.execute(
                    guests.getFirst(),
                    identifier,
                    new Game.Command(
                        cmd.id(), cmd.revision(), DRAW_COMMAND, Map.of(COUNT_FIELD, 2))))
        .hasMessageContaining("reused");
  }

  @Test
  void invalidActionAndDatabaseFailureRollBackWithoutRevisionChange() {
    final Map<String, Object> view = game();
    final String identifier = (String) view.get("id");
    assertThatThrownBy(
            () ->
                store.execute(
                    guests.getFirst(),
                    identifier,
                    command(rev(view), DRAW_COMMAND, Map.of(COUNT_FIELD, 100))))
        .hasMessageContaining("library");
    assertThat(store.view(guests.getFirst(), identifier)).isEqualTo(view);
    jdbc.execute(
        "CREATE FUNCTION reject_test_action() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
    jdbc.execute(
        "CREATE TRIGGER reject_action BEFORE INSERT ON actions FOR EACH ROW EXECUTE FUNCTION reject_test_action()");
    try {
      assertThatThrownBy(
              () ->
                  store.execute(
                      guests.getFirst(),
                      identifier,
                      command(rev(view), DRAW_COMMAND, Map.of(COUNT_FIELD, 1))))
          .isInstanceOf(RuntimeException.class);
      assertThat(store.view(guests.getFirst(), identifier)).isEqualTo(view);
    } finally {
      jdbc.execute("DROP TRIGGER reject_action ON actions");
      jdbc.execute("DROP FUNCTION reject_test_action()");
    }
  }

  @Test
  void simultaneousCommandsAcceptOneAndRejectStaleOther() throws Exception {
    final Map<String, Object> view = game();
    final String identifier = (String) view.get("id");
    final long rev = rev(view);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      final CountDownLatch gate = new CountDownLatch(1);
      final List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    store.execute(
                        guests.getFirst(),
                        identifier,
                        command(rev, DRAW_COMMAND, Map.of(COUNT_FIELD, 1)));
                    return true;
                  } catch (RoomStore.Conflict e) {
                    return false;
                  }
                }));
      }
      gate.countDown();
      int successes = 0;
      for (final Future<Boolean> future : futures) {
        if (future.get(10, TimeUnit.SECONDS)) {
          successes++;
        }
      }
      assertThat(successes).isEqualTo(1);
    }
    assertThat(rev(store.view(guests.getFirst(), identifier))).isEqualTo(rev + 1);
  }

  @Test
  void invitationsCannotReadOrReplaceOccupiedSeats() {
    final Map<String, Object> view = game();
    final String identifier = (String) view.get("id");
    assertThatThrownBy(() -> store.view(guests.get(4), identifier)).hasMessageContaining("member");
    assertThatThrownBy(() -> store.join(guests.get(4), identifier, "intruder"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(store.join(guests.getFirst(), identifier, "new name")).isEqualTo(view);
  }

  @Test
  void standardFormatAndPrivateStateSurviveRecoveryAndCommandsRemainIdempotent() {
    Map<String, Object> view = store.create(guests.get(0), "Standard A", GameFormat.STANDARD);
    final String identifier = (String) view.get("id");
    assertThat(view).containsEntry(FORMAT_FIELD, "STANDARD").containsEntry("seatCapacity", 2);
    store.join(guests.get(1), identifier, "Standard B");
    assertThatThrownBy(() -> store.join(guests.get(2), identifier, "Third"))
        .hasMessage("Room is full");
    for (int seat = 0; seat < 2; seat++) {
      final String name = "Private Standard " + seat;
      final Game.Definition def =
          new Game.Definition(
              name,
              name,
              "",
              "",
              "Basic Land",
              List.of(),
              true,
              List.of(),
              Map.of("standard", "legal"),
              System.currentTimeMillis());
      view = store.view(guests.get(seat), identifier);
      store.load(
          guests.get(seat),
          identifier,
          UUID.randomUUID().toString(),
          rev(view),
          Collections.nCopies(60, def),
          null);
      view = store.view(guests.get(seat), identifier);
      store.execute(guests.get(seat), identifier, command(rev(view), "READY", Map.of()));
    }
    view = store.view(guests.get(0), identifier);
    final Command start = command(rev(view), "START", Map.of("firstPlayer", 1));
    final Map<String, Object> started = store.execute(guests.get(0), identifier, start);
    assertThat(store.execute(guests.get(0), identifier, start)).isEqualTo(started);
    assertThat(started).containsEntry("active", 1);
    view =
        store.execute(
            guests.get(0),
            identifier,
            command(rev(started), "COUNTER", Map.of("name", "life", "delta", -1)));
    final RoomStore restarted =
        new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine());
    assertThat(restarted.view(guests.get(0), identifier)).isEqualTo(view);
    assertThat(
            new ObjectMapper()
                .valueToTree(view)
                .path("players")
                .get(0)
                .path("counters")
                .path("life")
                .asInt())
        .isEqualTo(19);
    assertThat(new ObjectMapper().valueToTree(view).path("cards").toString())
        .doesNotContain("Private Standard 1");
    assertThat(restarted.format(guests.get(0), identifier)).isEqualTo(GameFormat.STANDARD);
    assertThat(restarted.list(guests.get(0)))
        .anySatisfy(
            room ->
                assertThat(room)
                    .containsEntry("id", identifier)
                    .containsEntry(FORMAT_FIELD, "STANDARD"));
  }

  @Test
  void oldStoredSnapshotsAndSummariesDefaultToCommanderWithoutResettingLife() {
    Map<String, Object> view = store.create(guests.getFirst(), "Legacy");
    final String identifier = (String) view.get("id");
    jdbc.update(
        "UPDATE rooms SET snapshot=jsonb_set(snapshot - 'format','{players,0,counters,life}','17') WHERE id=?",
        identifier);
    view = store.view(guests.getFirst(), identifier);
    assertThat(view).containsEntry(FORMAT_FIELD, "COMMANDER").containsEntry("seatCapacity", 4);
    assertThat(
            new ObjectMapper()
                .valueToTree(view)
                .path("players")
                .get(0)
                .path("counters")
                .path("life")
                .asInt())
        .isEqualTo(17);
    assertThat(store.list(guests.getFirst()))
        .anySatisfy(
            room ->
                assertThat(room)
                    .containsEntry("id", identifier)
                    .containsEntry(FORMAT_FIELD, "COMMANDER"));
  }
}
