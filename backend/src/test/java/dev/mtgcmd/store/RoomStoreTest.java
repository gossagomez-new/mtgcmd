package dev.mtgcmd.store;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class RoomStoreTest {
  private static final String REVOKE_OPERATION = "REVOKE";
  private static final String APPLY_SCRY_COMMAND = "APPLY_SCRY";
  private static final String CLOSE_INSPECTION_COMMAND = "CLOSE_INSPECTION";
  private static final String INSPECTION_ID_FIELD = "inspectionId";
  private static final String ZONE_FIELD = "zone";
  private static final String ACTIONS_TABLE = "actions";
  private static final String COMMANDS_TABLE = "commands";
  private static final String INSPECTION_FIELD = "libraryInspection";
  private static final String CARD_IDS_FIELD = "cardIds";
  private static final String SNAPSHOT_SQL = "SELECT snapshot::text FROM rooms WHERE id=?";

  private static final String KEEP_HAND_COMMAND = "KEEP_HAND";
  private static final String MULLIGAN_COMMAND = "MULLIGAN";
  private static final String BOTTOM_FIELD = "bottom";
  private static final String DRAW_COMMAND = "DRAW";

  private static final String COUNT_FIELD = "count";

  private static final String FORMAT_FIELD = "format";

  private static final List<String> CLEANUP_OPERATIONS =
      List.of(
          REVOKE_OPERATION,
          "CONCEDE",
          "FINISH",
          DRAW_COMMAND,
          "SHUFFLE",
          "MOVE_IN",
          "MOVE_OUT",
          "EXILE_TOP",
          APPLY_SCRY_COMMAND,
          CLOSE_INSPECTION_COMMAND,
          "SEARCH");

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
    final Map<String, Object> opening = openingGame(format);
    return keepOpeningHands((String) opening.get("id"));
  }

  Map<String, Object> keepOpeningHands(final String identifier) {
    final int seats =
        new ObjectMapper()
            .valueToTree(store.view(guests.getFirst(), identifier))
            .path("players")
            .size();
    for (int seat = 0; seat < seats; seat++) {
      final Map<String, Object> current = store.view(guests.get(seat), identifier);
      store.execute(
          guests.get(seat),
          identifier,
          command(rev(current), KEEP_HAND_COMMAND, Map.of(BOTTOM_FIELD, List.of())));
    }
    return store.view(guests.getFirst(), identifier);
  }

  Map<String, Object> openingGame(final GameFormat format) {
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

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void openingHandsSurviveRecoveryAndDuplicateRedrawAndKeepRetries(final GameFormat format) {
    final Map<String, Object> opening = openingGame(format);
    final String identifier = (String) opening.get("id");
    final Command redraw = command(rev(opening), MULLIGAN_COMMAND, Map.of());
    Map<String, Object> current = store.execute(guests.getFirst(), identifier, redraw);
    assertThat(store.execute(guests.getFirst(), identifier, redraw)).isEqualTo(current);
    final RoomStore recovered =
        new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine());
    assertThat(recovered.view(guests.getFirst(), identifier)).isEqualTo(current);
    assertThatThrownBy(
            () ->
                recovered.execute(
                    guests.getFirst(),
                    identifier,
                    command(rev(opening), MULLIGAN_COMMAND, Map.of())))
        .isInstanceOf(RoomStore.Conflict.class);
    if (format.usesCommanders()) {
      current =
          recovered.execute(
              guests.getFirst(), identifier, command(rev(current), MULLIGAN_COMMAND, Map.of()));
    }
    final com.fasterxml.jackson.databind.JsonNode json = new ObjectMapper().valueToTree(current);
    final String cardId =
        java.util.stream.StreamSupport.stream(json.path("cards").spliterator(), false)
            .filter(
                c -> "hand".equals(c.path(ZONE_FIELD).asText()) && c.path("zoneOwner").asInt() == 0)
            .findFirst()
            .orElseThrow()
            .path("id")
            .asText();
    final Command keep =
        command(rev(current), KEEP_HAND_COMMAND, Map.of(BOTTOM_FIELD, List.of(cardId)));
    final Map<String, Object> kept = recovered.execute(guests.getFirst(), identifier, keep);
    assertThat(recovered.execute(guests.getFirst(), identifier, keep)).isEqualTo(kept);
    assertThat(new ObjectMapper().valueToTree(kept).at("/mulligans/players/0/kept").asBoolean())
        .isTrue();
    assertThat(new ObjectMapper().valueToTree(kept).at("/players/0/counts/hand").asInt())
        .isEqualTo(6);
    assertThat(
            new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine())
                .view(guests.getFirst(), identifier))
        .isEqualTo(kept);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void openingPersistenceFailuresRollBackCardsProgressAndCommandRecords(final GameFormat format) {
    for (final String type : List.of(MULLIGAN_COMMAND, KEEP_HAND_COMMAND)) {
      final Map<String, Object> opening = openingGame(format);
      final String identifier = (String) opening.get("id");
      final Command action = command(rev(opening), type, Map.of(BOTTOM_FIELD, List.of()));
      jdbc.execute(
          "CREATE FUNCTION reject_opening_action() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'opening persistence failure'; END $$");
      jdbc.execute(
          "CREATE TRIGGER reject_opening BEFORE INSERT ON actions FOR EACH ROW EXECUTE FUNCTION reject_opening_action()");
      try {
        assertThatThrownBy(() -> store.execute(guests.getFirst(), identifier, action))
            .isInstanceOf(DataAccessException.class);
        assertThat(store.view(guests.getFirst(), identifier)).isEqualTo(opening);
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM commands WHERE room_id=? AND command_id=?",
                    Integer.class,
                    identifier,
                    action.id()))
            .isZero();
      } finally {
        jdbc.execute("DROP TRIGGER reject_opening ON actions");
        jdbc.execute("DROP FUNCTION reject_opening_action()");
      }
      final Map<String, Object> retried = store.execute(guests.getFirst(), identifier, action);
      assertThat(rev(retried)).isEqualTo(rev(opening) + 1);
      assertThat(store.execute(guests.getFirst(), identifier, action)).isEqualTo(retried);
    }
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
    final String saved = jdbc.queryForObject(SNAPSHOT_SQL, String.class, identifier);
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
          .isInstanceOf(DataAccessException.class);
      assertThat(store.view(guests.get(1), identifier)).isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM commands WHERE room_id=? AND command_id=?",
                  Integer.class,
                  identifier,
                  giveBack.id()))
          .isZero();
      final String saved = jdbc.queryForObject(SNAPSHOT_SQL, String.class, identifier);
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
    store.execute(guests.getFirst(), identifier, command(rev(view), "START", Map.of()));
    return keepOpeningHands(identifier);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void inspectionCleanupIsPersistedAndRecoveredWithoutResurrection(final GameFormat format)
      throws JsonProcessingException {
    for (final String operation : CLEANUP_OPERATIONS) {
      final InspectionRoom room = inspectionRoom(format, operation);
      final Game before = room.before();
      final Map<Integer, Game.LibraryInspection> expected = retainedInspections(before, operation);
      final Command cleanup = inspectionCleanupCommand(room, operation);
      final int events = savedRows(ACTIONS_TABLE, room.id());
      final int commands = savedRows(COMMANDS_TABLE, room.id());
      final Map<String, Object> result = store.execute(guests.getFirst(), room.id(), cleanup);
      final Game persisted = persistedGame(room.id());
      assertThat(persisted.libraryInspections).as(operation).isEqualTo(expected);
      assertThat(persisted.revision).isEqualTo(before.revision + 1);
      final RoomStore recovered =
          new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine());
      assertThat(recovered.execute(guests.getFirst(), room.id(), cleanup)).isEqualTo(result);
      for (int seat = 0; seat < format.seatCapacity(); seat++) {
        assertThat(recovered.view(guests.get(seat), room.id()))
            .isEqualTo(store.view(guests.get(seat), room.id()));
      }
      assertThat(persistedGame(room.id()).libraryInspections).isEqualTo(expected);
      assertThat(savedRows(ACTIONS_TABLE, room.id())).isEqualTo(events + 1);
      assertThat(savedRows(COMMANDS_TABLE, room.id())).isEqualTo(commands + 1);
    }
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void failedInspectionCleanupRollsBackStoredSessionsAndDurableHistory(final GameFormat format)
      throws JsonProcessingException {
    for (final String operation : CLEANUP_OPERATIONS) {
      final InspectionRoom room = inspectionRoom(format, operation);
      final Command cleanup = inspectionCleanupCommand(room, operation);
      final String saved = savedSnapshot(room.id());
      final int events = savedRows(ACTIONS_TABLE, room.id());
      final int commands = savedRows(COMMANDS_TABLE, room.id());
      final Map<String, Object> visible = store.view(guests.getFirst(), room.id());
      jdbc.execute(
          "CREATE FUNCTION reject_inspection_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'inspection cleanup persistence failure'; END $$");
      jdbc.execute(
          "CREATE TRIGGER reject_inspection_cleanup BEFORE INSERT ON actions FOR EACH ROW EXECUTE FUNCTION reject_inspection_cleanup()");
      try {
        assertThatThrownBy(() -> store.execute(guests.getFirst(), room.id(), cleanup))
            .as(operation)
            .isInstanceOf(DataAccessException.class);
        assertThat(savedSnapshot(room.id())).isEqualTo(saved);
        assertThat(persistedGame(room.id()).libraryInspections)
            .isEqualTo(room.before().libraryInspections);
        final RoomStore recovered =
            new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine());
        assertThat(recovered.view(guests.getFirst(), room.id())).isEqualTo(visible);
        assertThat(savedRows(ACTIONS_TABLE, room.id())).isEqualTo(events);
        assertThat(savedRows(COMMANDS_TABLE, room.id())).isEqualTo(commands);
      } finally {
        jdbc.execute("DROP TRIGGER reject_inspection_cleanup ON actions");
        jdbc.execute("DROP FUNCTION reject_inspection_cleanup()");
      }
      final RoomStore recovered =
          new RoomStore(jdbc, transactions, new ObjectMapper(), new GameEngine());
      final Map<String, Object> result = recovered.execute(guests.getFirst(), room.id(), cleanup);
      assertThat(persistedGame(room.id()).libraryInspections)
          .isEqualTo(retainedInspections(room.before(), operation));
      assertThat(recovered.execute(guests.getFirst(), room.id(), cleanup)).isEqualTo(result);
      assertThat(savedRows(ACTIONS_TABLE, room.id())).isEqualTo(events + 1);
      assertThat(savedRows(COMMANDS_TABLE, room.id())).isEqualTo(commands + 1);
    }
  }

  private InspectionRoom inspectionRoom(final GameFormat format, final String operation)
      throws JsonProcessingException {
    final String id = (String) game(format).get("id");
    executeInspectionSetup(id, 0, "GRANT", Map.of("target", 1));
    if ("MOVE_OUT".equals(operation)) {
      executeInspectionSetup(id, 0, "SEARCH", Map.of());
    } else {
      executeInspectionSetup(id, 0, "SCRY", Map.of(COUNT_FIELD, 2));
    }
    executeInspectionSetup(id, 1, "LOOK_TOP", Map.of("target", 0, COUNT_FIELD, 2));
    if (format.seatCapacity() > 2) {
      executeInspectionSetup(id, 2, "LOOK_TOP", Map.of(COUNT_FIELD, 1));
    }
    return new InspectionRoom(id, persistedGame(id));
  }

  private void executeInspectionSetup(
      final String id, final int seat, final String type, final Map<String, Object> data) {
    final Map<String, Object> view = store.view(guests.get(seat), id);
    store.execute(guests.get(seat), id, command(rev(view), type, data));
  }

  private static Command inspectionCleanupCommand(
      final InspectionRoom room, final String operation) {
    final Game before = room.before();
    final String session =
        before.libraryInspections.containsKey(0) ? before.libraryInspections.get(0).id() : "";
    final Map<String, Object> data =
        switch (operation) {
          case REVOKE_OPERATION -> Map.of("target", 1, "allow", false);
          case "MOVE_IN" ->
              Map.of(
                  "cardId",
                  before.cards.stream()
                      .filter(card -> card.zoneOwner == 0 && "hand".equals(card.zone))
                      .findFirst()
                      .orElseThrow()
                      .id,
                  ZONE_FIELD,
                  "library");
          case "MOVE_OUT" ->
              Map.of(
                  "cardId",
                  before.cards.stream()
                      .filter(card -> card.zoneOwner == 0 && "library".equals(card.zone))
                      .toList()
                      .getLast()
                      .id,
                  ZONE_FIELD,
                  "hand");
          case APPLY_SCRY_COMMAND ->
              Map.of(
                  INSPECTION_ID_FIELD,
                  session,
                  "top",
                  List.of(before.libraryInspections.get(0).cardIds().get(1)),
                  BOTTOM_FIELD,
                  List.of(before.libraryInspections.get(0).cardIds().getFirst()));
          case CLOSE_INSPECTION_COMMAND -> Map.of(INSPECTION_ID_FIELD, session);
          default -> Map.of();
        };
    final String type =
        REVOKE_OPERATION.equals(operation)
            ? "GRANT"
            : operation.startsWith("MOVE_") ? "MOVE" : operation;
    return command(before.revision, type, data);
  }

  private static Map<Integer, Game.LibraryInspection> retainedInspections(
      final Game before, final String operation) {
    final Map<Integer, Game.LibraryInspection> result =
        new LinkedHashMap<>(before.libraryInspections);
    if ("FINISH".equals(operation)) {
      result.clear();
    } else if (REVOKE_OPERATION.equals(operation)) {
      result.remove(1);
    } else if ("SEARCH".equals(operation) || CLOSE_INSPECTION_COMMAND.equals(operation)) {
      result.remove(0);
    } else {
      result.values().removeIf(inspection -> inspection.target() == 0);
    }
    return result;
  }

  private static Game persistedGame(final String id) throws JsonProcessingException {
    return new ObjectMapper().readValue(savedSnapshot(id), Game.class);
  }

  private static String savedSnapshot(final String id) {
    return jdbc.queryForObject(SNAPSHOT_SQL, String.class, id);
  }

  private static int savedRows(final String table, final String id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE room_id=?", Integer.class, id);
  }

  private record InspectionRoom(String id, Game before) {}

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
        new ObjectMapper().valueToTree(opened.get(INSPECTION_FIELD));
    final List<String> cards = new ArrayList<>();
    session.path(CARD_IDS_FIELD).forEach(value -> cards.add(value.asText()));
    for (final String cardId : cards)
      assertThat(recovered.view(guests.get(1), id).toString()).doesNotContain(cardId);
    final Command apply =
        command(
            rev(opened),
            APPLY_SCRY_COMMAND,
            Map.of(
                INSPECTION_ID_FIELD,
                session.path("id").asText(),
                "top",
                List.of(cards.get(2)),
                BOTTOM_FIELD,
                List.of(cards.get(1), cards.get(0))));
    final Map<String, Object> result = recovered.execute(guests.getFirst(), id, apply);
    assertThat(result.get(INSPECTION_FIELD)).isNull();
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
                    new Command(
                        apply.id(), apply.revision(), CLOSE_INSPECTION_COMMAND, apply.data())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                recovered.execute(
                    guests.getFirst(), id, command(rev(opened), APPLY_SCRY_COMMAND, apply.data())))
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
        new ObjectMapper().valueToTree(opened.get(INSPECTION_FIELD));
    final Command apply =
        command(
            rev(opened),
            APPLY_SCRY_COMMAND,
            Map.of(
                INSPECTION_ID_FIELD,
                session.path("id").asText(),
                "top",
                List.of(),
                BOTTOM_FIELD,
                List.of(session.path(CARD_IDS_FIELD).get(0).asText())));
    final String saved = jdbc.queryForObject(SNAPSHOT_SQL, String.class, id);
    jdbc.execute(
        "CREATE FUNCTION reject_scry_action() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'scry persistence failure'; END $$");
    jdbc.execute(
        "CREATE TRIGGER reject_scry BEFORE INSERT ON actions FOR EACH ROW EXECUTE FUNCTION reject_scry_action()");
    try {
      assertThatThrownBy(() -> store.execute(guests.getFirst(), id, apply))
          .isInstanceOf(DataAccessException.class);
      assertThat(store.view(guests.getFirst(), id)).isEqualTo(opened);
      assertThat(jdbc.queryForObject(SNAPSHOT_SQL, String.class, id)).isEqualTo(saved);
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
    assertThat(store.execute(guests.getFirst(), id, apply).get(INSPECTION_FIELD)).isNull();
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
            command(rev(view), "MOVE", Map.of("cardId", cardId, ZONE_FIELD, "battlefield")));
    final Command batch =
        command(rev(view), "TAP_SELECTED", Map.of(CARD_IDS_FIELD, List.of(cardId), "tapped", true));
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
                        Map.of(CARD_IDS_FIELD, List.of(cardId, "missing"), "tapped", false))))
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
    final Map<String, Object> kept = keepOpeningHands(identifier);
    view =
        store.execute(
            guests.get(0),
            identifier,
            command(rev(kept), "COUNTER", Map.of("name", "life", "delta", -1)));
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

  @Nested
  class ResponseContracts {
    private static final String HOST_NAME = "Host";
    private static final String GUEST_NAME = "Guest";
    private static final String REGISTERED_ID = "registered";
    private static final String JOIN_DESCRIPTION = "joined";
    private static final String DECK_COUNT_FIELD = "deckCount";
    private static final String DECK_REVISION_FIELD = "deckRevision";
    private static final String SEAT_FIELD = "seat";
    private static final String ROOM_FIELD = "id";
    private static final String PLAYERS_FIELD = "players";
    private static final String NAME_FIELD = "name";
    private static final String READY_COMMAND = "READY";
    private static final String READY_FIELD = "ready";
    private static final String MEMBERS_TABLE = "members";
    private static final String PRIVATE_NAME = "Private registered card";
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(GameFormat.class)
    void createAndJoinReturnCorrectCallerViewsAndDurableEvents(final GameFormat format)
        throws JsonProcessingException {
      final Map<String, Object> created = store.create(guests.getFirst(), " Host ", format);
      final String room = (String) created.get(ROOM_FIELD);
      assertThatCode(() -> UUID.fromString(room)).doesNotThrowAnyException();
      assertResponse(created, room, 0, 1, format, 1);
      assertThat(mapper.valueToTree(created).path(PLAYERS_FIELD).get(0).path(NAME_FIELD).asText())
          .isEqualTo(HOST_NAME);
      assertEvent(room, 1, 0, HOST_NAME, JOIN_DESCRIPTION);
      assertThat(savedRows(MEMBERS_TABLE, room)).isEqualTo(1);
      assertThat(savedRows(COMMANDS_TABLE, room)).isZero();
      final Map<String, Object> joined = store.join(guests.get(1), room, " Guest ");
      assertResponse(joined, room, 1, 2, format, 2);
      assertThat(mapper.valueToTree(joined).path(PLAYERS_FIELD).get(1).path(NAME_FIELD).asText())
          .isEqualTo(GUEST_NAME);
      assertThat(store.seat(guests.get(1), room)).isEqualTo(1);
      assertEvent(room, 2, 1, GUEST_NAME, JOIN_DESCRIPTION);
      assertThat(savedRows(ACTIONS_TABLE, room)).isEqualTo(2);
      assertThat(savedRows(MEMBERS_TABLE, room)).isEqualTo(2);
      assertThat(savedRows(COMMANDS_TABLE, room)).isZero();
      assertThat(recovered().view(guests.get(1), room)).isEqualTo(joined);
    }

    @ParameterizedTest
    @EnumSource(GameFormat.class)
    void loadAndExecuteReturnCommittedActorStateAndExactlyOneEvent(final GameFormat format)
        throws JsonProcessingException {
      final String room = lobby(format);
      final List<Game.Definition> deck = registeredDeck();
      final String loadId = UUID.randomUUID().toString();
      final Map<String, Object> loaded =
          store.load(guests.get(1), room, loadId, 2, deck, commanders(format));
      assertResponse(loaded, room, 1, 3, format, 2);
      final JsonNode player = mapper.valueToTree(loaded).path(PLAYERS_FIELD).get(1);
      assertThat(player.path(DECK_COUNT_FIELD).asInt()).isEqualTo(60);
      assertThat(player.path(DECK_REVISION_FIELD).asLong()).isEqualTo(3);
      assertThat(player.path(READY_FIELD).asBoolean()).isFalse();
      assertThat(persistedGame(room).players.get(1).deck).isEqualTo(deck);
      assertEvent(room, 3, 1, GUEST_NAME, "imported", "60");
      final Map<String, Object> ready =
          store.execute(
              guests.get(1), room, command(3, READY_COMMAND, Map.of("acceptWarnings", true)));
      assertResponse(ready, room, 1, 4, format, 2);
      assertThat(mapper.valueToTree(ready).path(PLAYERS_FIELD).get(1).path(READY_FIELD).asBoolean())
          .isTrue();
      assertEvent(room, 4, 1, GUEST_NAME, "ready");
      assertThat(savedRows(ACTIONS_TABLE, room)).isEqualTo(4);
      assertThat(savedRows(COMMANDS_TABLE, room)).isEqualTo(2);
      assertThat(store.view(guests.getFirst(), room).toString()).doesNotContain(PRIVATE_NAME);
      assertThat(recovered().view(guests.get(1), room)).isEqualTo(ready);
    }

    @ParameterizedTest
    @EnumSource(GameFormat.class)
    void retriesReturnCurrentStateWithoutDuplicatingHistoryOrChangingMembership(
        final GameFormat format) {
      final String room = lobby(format);
      final String loadId = UUID.randomUUID().toString();
      final List<Game.Definition> deck = registeredDeck();
      store.load(guests.get(1), room, loadId, 2, deck, commanders(format));
      final Command ready = command(3, READY_COMMAND, Map.of("acceptWarnings", true));
      store.execute(guests.get(1), room, ready);
      final Map<String, Object> current =
          store.execute(guests.get(1), room, command(4, READY_COMMAND, Map.of(READY_FIELD, false)));
      final StoredContract before = stored(room);
      final RoomStore restarted = recovered();
      assertThat(restarted.load(guests.get(1), room, loadId, 2, deck, commanders(format)))
          .isEqualTo(current);
      assertThat(restarted.execute(guests.get(1), room, ready)).isEqualTo(current);
      assertThat(restarted.join(guests.get(1), room, "Renamed")).isEqualTo(current);
      assertThat(
              mapper.valueToTree(current).path(PLAYERS_FIELD).get(1).path(READY_FIELD).asBoolean())
          .isFalse();
      assertThat(mapper.valueToTree(current).path(PLAYERS_FIELD).get(1).path(NAME_FIELD).asText())
          .isEqualTo(GUEST_NAME);
      assertThatThrownBy(
              () ->
                  restarted.execute(
                      guests.get(1),
                      room,
                      new Command(
                          ready.id(), ready.revision(), READY_COMMAND, Map.of(READY_FIELD, false))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reused");
      assertThatThrownBy(
              () -> restarted.load(guests.get(1), room, loadId, 2, List.of(), commanders(format)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reused");
      assertThat(stored(room)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(GameFormat.class)
    void commandIdsAreScopedToRoomAndSeat(final GameFormat format) {
      final String first = lobby(format);
      final String shared = UUID.randomUUID().toString();
      final List<Game.Definition> deck = registeredDeck();
      store.load(guests.getFirst(), first, shared, 2, deck, commanders(format));
      final Map<String, Object> otherSeat =
          store.load(guests.get(1), first, shared, 3, deck, commanders(format));
      assertResponse(otherSeat, first, 1, 4, format, 2);
      assertThat(savedRows(COMMANDS_TABLE, first)).isEqualTo(2);
      final Map<String, Object> second = store.create(guests.getFirst(), "Other room", format);
      final String room = (String) second.get(ROOM_FIELD);
      final Map<String, Object> otherRoom =
          store.load(guests.getFirst(), room, shared, 1, deck, commanders(format));
      assertResponse(otherRoom, room, 0, 2, format, 1);
      assertThat(savedRows(COMMANDS_TABLE, room)).isEqualTo(1);
      assertThat(recovered().view(guests.get(1), first)).isEqualTo(otherSeat);
    }

    @ParameterizedTest
    @EnumSource(GameFormat.class)
    void rejectedInputsAndStaleOrUnauthorizedRequestsLeaveNoPartialHistory(
        final GameFormat format) {
      final Map<String, Object> created = store.create(guests.getFirst(), HOST_NAME, format);
      final String room = (String) created.get(ROOM_FIELD);
      final StoredContract original = stored(room);
      final String missing = UUID.randomUUID().toString();
      final List<Integer> counts = databaseCounts();
      assertThatThrownBy(() -> store.join(guests.getFirst(), missing, GUEST_NAME))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Room not found");
      for (final Command malformed :
          Arrays.asList(
              null,
              new Command(UUID.randomUUID().toString(), 1, null, Map.of()),
              new Command(UUID.randomUUID().toString(), 1, READY_COMMAND, null))) {
        assertThatThrownBy(() -> store.execute(guests.get(4), missing, malformed))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid command");
      }
      assertThat(databaseCounts()).isEqualTo(counts);
      assertThatThrownBy(() -> store.join(guests.get(1), room, " "))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(stored(room)).isEqualTo(original);
      store.join(guests.get(1), room, GUEST_NAME);
      final StoredContract joined = stored(room);
      assertThatThrownBy(
              () ->
                  store.load(
                      guests.get(1),
                      room,
                      UUID.randomUUID().toString(),
                      2,
                      List.of(),
                      commanders(format)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(
              () ->
                  store.load(
                      guests.get(1),
                      room,
                      UUID.randomUUID().toString(),
                      1,
                      registeredDeck(),
                      commanders(format)))
          .isInstanceOf(RoomStore.Conflict.class);
      assertThatThrownBy(
              () -> store.execute(guests.getFirst(), room, command(1, READY_COMMAND, Map.of())))
          .isInstanceOf(RoomStore.Conflict.class);
      assertThatThrownBy(
              () ->
                  store.load(
                      guests.get(4),
                      room,
                      UUID.randomUUID().toString(),
                      2,
                      registeredDeck(),
                      commanders(format)))
          .isInstanceOf(RoomStore.Forbidden.class);
      assertThatThrownBy(
              () -> store.execute(guests.get(4), room, command(2, READY_COMMAND, Map.of())))
          .isInstanceOf(RoomStore.Forbidden.class);
      assertThat(stored(room)).isEqualTo(joined);
      for (int seat = 2; seat < format.seatCapacity(); seat++) {
        store.join(guests.get(seat), room, "Player " + seat);
      }
      final StoredContract full = stored(room);
      assertThatThrownBy(() -> store.join(guests.get(4), room, "Extra")).hasMessage("Room is full");
      assertThat(stored(room)).isEqualTo(full);
      assertThat(recovered().view(guests.getFirst(), room))
          .isEqualTo(store.view(guests.getFirst(), room));
    }

    @ParameterizedTest
    @EnumSource(GameFormat.class)
    void failedCreateJoinAndLoadRollBackAllRowsAndAllowExplicitRetries(final GameFormat format)
        throws JsonProcessingException {
      final List<Integer> counts = databaseCounts();
      rejectEvents(() -> store.create(guests.getFirst(), HOST_NAME, format));
      assertThat(databaseCounts()).isEqualTo(counts);
      final Map<String, Object> created = store.create(guests.getFirst(), HOST_NAME, format);
      final String room = (String) created.get(ROOM_FIELD);
      assertResponse(created, room, 0, 1, format, 1);
      assertEvent(room, 1, 0, HOST_NAME, JOIN_DESCRIPTION);
      final StoredContract beforeJoin = stored(room);
      rejectEvents(() -> store.join(guests.get(1), room, GUEST_NAME));
      assertThat(stored(room)).isEqualTo(beforeJoin);
      assertThat(recovered().list(guests.get(1))).isEmpty();
      final Map<String, Object> joined = store.join(guests.get(1), room, GUEST_NAME);
      assertResponse(joined, room, 1, 2, format, 2);
      assertEvent(room, 2, 1, GUEST_NAME, JOIN_DESCRIPTION);
      final StoredContract beforeLoad = stored(room);
      final String loadId = UUID.randomUUID().toString();
      final List<Game.Definition> deck = registeredDeck();
      rejectEvents(() -> store.load(guests.get(1), room, loadId, 2, deck, commanders(format)));
      assertThat(stored(room)).isEqualTo(beforeLoad);
      assertThat(recovered().view(guests.get(1), room)).isEqualTo(joined);
      final Map<String, Object> loaded =
          recovered().load(guests.get(1), room, loadId, 2, deck, commanders(format));
      assertResponse(loaded, room, 1, 3, format, 2);
      assertEvent(room, 3, 1, GUEST_NAME, "imported");
      assertThat(recovered().load(guests.get(1), room, loadId, 2, deck, commanders(format)))
          .isEqualTo(loaded);
      assertThat(savedRows(COMMANDS_TABLE, room)).isEqualTo(1);
      assertThat(savedRows(ACTIONS_TABLE, room)).isEqualTo(3);
    }

    @Test
    void createHasNoIdempotencyKeyAndNullFormatDefaultsToCommander()
        throws JsonProcessingException {
      final Map<String, Object> first = store.create(guests.getFirst(), HOST_NAME, null);
      final Map<String, Object> second = store.create(guests.getFirst(), HOST_NAME, null);
      assertThat(second.get(ROOM_FIELD)).isNotEqualTo(first.get(ROOM_FIELD));
      for (final Map<String, Object> view : List.of(first, second)) {
        final String room = (String) view.get(ROOM_FIELD);
        assertResponse(view, room, 0, 1, GameFormat.COMMANDER, 1);
        assertEvent(room, 1, 0, HOST_NAME, JOIN_DESCRIPTION);
        assertThat(savedRows(ACTIONS_TABLE, room)).isEqualTo(1);
      }
    }

    private String lobby(final GameFormat format) {
      final Map<String, Object> created = store.create(guests.getFirst(), HOST_NAME, format);
      final String room = (String) created.get(ROOM_FIELD);
      store.join(guests.get(1), room, GUEST_NAME);
      return room;
    }

    private List<Game.Definition> registeredDeck() {
      final Game.Definition card =
          new Game.Definition(
              REGISTERED_ID,
              PRIVATE_NAME,
              "",
              "",
              "Legendary Creature",
              List.of(),
              true,
              List.of(),
              Map.of("standard", "legal"),
              System.currentTimeMillis());
      return Collections.nCopies(60, card);
    }

    private List<String> commanders(final GameFormat format) {
      return format.usesCommanders() ? List.of(REGISTERED_ID) : List.of();
    }

    private RoomStore recovered() {
      return new RoomStore(jdbc, transactions, mapper, new GameEngine());
    }

    private void assertResponse(
        final Map<String, Object> view,
        final String room,
        final int seat,
        final long revision,
        final GameFormat format,
        final int players) {
      assertThat(view)
          .containsEntry(ROOM_FIELD, room)
          .containsEntry("you", seat)
          .containsEntry("revision", revision)
          .containsEntry(FORMAT_FIELD, format.name())
          .containsEntry("seatCapacity", format.seatCapacity())
          .containsEntry("status", "lobby");
      assertThat(mapper.valueToTree(view).path(PLAYERS_FIELD).size()).isEqualTo(players);
      assertThat(view).isEqualTo(store.view(guests.get(seat), room));
    }

    private void assertEvent(
        final String room, final long revision, final int seat, final String... descriptions)
        throws JsonProcessingException {
      final List<Map<String, Object>> rows =
          jdbc.queryForList(
              "SELECT seat,description FROM actions WHERE room_id=? AND revision=?",
              room,
              revision);
      assertThat(rows).hasSize(1);
      assertThat(rows.getFirst()).containsEntry(SEAT_FIELD, seat);
      final String description = (String) rows.getFirst().get("description");
      assertThat(description).isEqualTo(persistedGame(room).log.getLast()).contains(descriptions);
      assertThat(description).doesNotContain(PRIVATE_NAME);
    }

    private StoredContract stored(final String room) {
      return new StoredContract(
          savedSnapshot(room),
          savedRows(MEMBERS_TABLE, room),
          savedRows(ACTIONS_TABLE, room),
          savedRows(COMMANDS_TABLE, room));
    }

    private List<Integer> databaseCounts() {
      return List.of("rooms", MEMBERS_TABLE, ACTIONS_TABLE, COMMANDS_TABLE).stream()
          .map(table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class))
          .toList();
    }

    private void rejectEvents(final Runnable action) {
      jdbc.execute(
          "CREATE FUNCTION reject_contract_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'contract event persistence failure'; END $$");
      jdbc.execute(
          "CREATE TRIGGER reject_contract_event BEFORE INSERT ON actions FOR EACH ROW EXECUTE FUNCTION reject_contract_event()");
      try {
        assertThatThrownBy(action::run).isInstanceOf(DataAccessException.class);
      } finally {
        jdbc.execute("DROP TRIGGER reject_contract_event ON actions");
        jdbc.execute("DROP FUNCTION reject_contract_event()");
      }
    }

    private record StoredContract(String snapshot, int members, int events, int commands) {}
  }
}
