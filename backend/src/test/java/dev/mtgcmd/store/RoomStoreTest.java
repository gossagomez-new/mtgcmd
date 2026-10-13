package dev.mtgcmd.store;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class RoomStoreTest {
  static EmbeddedPostgres pg;
  static JdbcTemplate jdbc;
  static TransactionTemplate tx;
  RoomStore store;
  List<String> guests;

  @BeforeAll
  static void database() throws Exception {
    pg = EmbeddedPostgres.builder().setPort(0).start();
    var dataSource = pg.getPostgresDatabase();
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @AfterAll
  static void close() throws Exception {
    if (pg != null) pg.close();
  }

  @BeforeEach
  void setup() {
    store = new RoomStore(jdbc, tx, new ObjectMapper(), new GameEngine());
    guests = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      String guest = UUID.randomUUID().toString();
      guests.add(guest);
      jdbc.update("INSERT INTO guests(token_hash,csrf_token) VALUES (?,?)", guest, "csrf");
    }
  }

  Map<String, Object> game() {
    Map<String, Object> view = store.create(guests.getFirst(), "A");
    String id = (String) view.get("id");
    for (int i = 1; i < 4; i++) store.join(guests.get(i), id, "Player " + i);
    Game.Definition card =
        new Game.Definition(
            "island", "Island", "", "", "Basic Land — Island", List.of("U"), true, List.of());
    for (int i = 0; i < 4; i++) {
      view = store.view(guests.get(i), id);
      store.load(
          guests.get(i),
          id,
          UUID.randomUUID().toString(),
          rev(view),
          Collections.nCopies(20, card),
          List.of("island"));
      view = store.view(guests.get(i), id);
      store.execute(guests.get(i), id, command(rev(view), "READY", Map.of("acceptWarnings", true)));
    }
    view = store.view(guests.getFirst(), id);
    return store.execute(guests.getFirst(), id, command(rev(view), "START", Map.of()));
  }

  static long rev(Map<String, Object> v) {
    return ((Number) v.get("revision")).longValue();
  }

  static Game.Command command(long rev, String type, Map<String, Object> data) {
    return new Game.Command(UUID.randomUUID().toString(), rev, type, data);
  }

  @Test
  void duplicateCommandsApplyOnceAndNewStoreRecoversState() {
    var v = game();
    String id = (String) v.get("id");
    var cmd = command(rev(v), "DRAW", Map.of("count", 1));
    var result = store.execute(guests.getFirst(), id, cmd);
    assertThat(store.execute(guests.getFirst(), id, cmd)).isEqualTo(result);
    var restarted = new RoomStore(jdbc, tx, new ObjectMapper(), new GameEngine());
    assertThat(restarted.view(guests.getFirst(), id)).isEqualTo(result);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commands WHERE room_id=? AND command_id=?",
                Integer.class,
                id,
                cmd.id()))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                store.execute(
                    guests.getFirst(),
                    id,
                    new Game.Command(cmd.id(), cmd.revision(), "DRAW", Map.of("count", 2))))
        .hasMessageContaining("reused");
  }

  @Test
  void invalidActionAndDatabaseFailureRollBackWithoutRevisionChange() {
    var v = game();
    String id = (String) v.get("id");
    assertThatThrownBy(
            () ->
                store.execute(guests.getFirst(), id, command(rev(v), "DRAW", Map.of("count", 100))))
        .hasMessageContaining("library");
    assertThat(store.view(guests.getFirst(), id)).isEqualTo(v);
    jdbc.execute(
        "CREATE FUNCTION reject_test_action() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
    jdbc.execute(
        "CREATE TRIGGER reject_action BEFORE INSERT ON actions FOR EACH ROW EXECUTE FUNCTION reject_test_action()");
    try {
      assertThatThrownBy(
              () ->
                  store.execute(guests.getFirst(), id, command(rev(v), "DRAW", Map.of("count", 1))))
          .isInstanceOf(RuntimeException.class);
      assertThat(store.view(guests.getFirst(), id)).isEqualTo(v);
    } finally {
      jdbc.execute("DROP TRIGGER reject_action ON actions");
      jdbc.execute("DROP FUNCTION reject_test_action()");
    }
  }

  @Test
  void simultaneousCommandsAcceptOneAndRejectStaleOther() throws Exception {
    var v = game();
    String id = (String) v.get("id");
    long rev = rev(v);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    store.execute(guests.getFirst(), id, command(rev, "DRAW", Map.of("count", 1)));
                    return true;
                  } catch (RoomStore.Conflict e) {
                    return false;
                  }
                }));
      gate.countDown();
      int successes = 0;
      for (var future : futures) if (future.get(10, TimeUnit.SECONDS)) successes++;
      assertThat(successes).isEqualTo(1);
    }
    assertThat(rev(store.view(guests.getFirst(), id))).isEqualTo(rev + 1);
  }

  @Test
  void invitationsCannotReadOrReplaceOccupiedSeats() {
    var v = game();
    String id = (String) v.get("id");
    assertThatThrownBy(() -> store.view(guests.get(4), id)).hasMessageContaining("member");
    assertThatThrownBy(() -> store.join(guests.get(4), id, "intruder"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(store.join(guests.getFirst(), id, "new name")).isEqualTo(v);
  }
}
