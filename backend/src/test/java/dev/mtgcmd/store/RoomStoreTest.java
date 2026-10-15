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
    Map<String, Object> view = store.create(guests.getFirst(), "A");
    final String identifier = (String) view.get("id");
    for (int i = 1; i < 4; i++) {
      store.join(guests.get(i), identifier, "Player " + i);
    }
    final Game.Definition card =
        new Game.Definition(
            "island", "Island", "", "", "Basic Land — Island", List.of("U"), true, List.of());
    for (int i = 0; i < 4; i++) {
      view = store.view(guests.get(i), identifier);
      store.load(
          guests.get(i),
          identifier,
          UUID.randomUUID().toString(),
          rev(view),
          Collections.nCopies(20, card),
          List.of("island"));
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
