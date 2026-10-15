package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.catalog.*;
import dev.mtgcmd.game.Game;
import dev.mtgcmd.game.GameFormat;
import dev.mtgcmd.store.DeckDraftRepository;
import dev.mtgcmd.store.RoomStore;
import dev.mtgcmd.testsupport.LogCapture;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.InOrder;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiRoutesTest {
  private static final String ROOMS_PATH = "/api/rooms";

  private static final String DELETE_SQL = "DELETE";

  private static final String INSERT_SQL = "INSERT";

  private static final String FAILURE_MESSAGE = "broken";

  private static final String CARD_ID = "card";

  private static final String COMMAND_ID = "cmd";

  private static final String CSRF_TOKEN = "csrf";

  private static final String GUEST_ID = "guest";

  private static final String MESSAGE_FIELD = "message";

  private static final String ROOM_ID = "room";

  private static final String PREVIEW_PATH = "/api/rooms/room/decks/preview";
  private static final String LOAD_PATH = "/api/rooms/room/deck";
  private static final String LOAD_BODY =
      "{\"draftId\":\"draft\",\"commanders\":[\"card\"],\"commandId\":\"cmd\",\"revision\":6}";

  private final GuestSessions guests = mock(GuestSessions.class);
  private final RoomStore rooms = mock(RoomStore.class);
  private final DeckImporter importer = mock(DeckImporter.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ObjectMapper mapper = spy(new ObjectMapper());
  private final GameSocket socket = mock(GameSocket.class);
  private MockMvc mvc;
  private final Game.Definition card =
      new Game.Definition(
          CARD_ID, "Card", "", "", "Legendary Creature", List.of(), true, List.of());
  private final Map<String, Object> view = Map.of("revision", 7, "you", 0);

  @BeforeEach
  void setup() {
    when(guests.require(any())).thenReturn(new GuestSessions.Guest(GUEST_ID, CSRF_TOKEN));
    when(rooms.format(GUEST_ID, ROOM_ID)).thenReturn(GameFormat.COMMANDER);
    final DeckDraftRepository drafts = new DeckDraftRepository(jdbc, mapper);
    final DeckImportService imports = new DeckImportService(rooms, importer, drafts);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new SessionController(guests),
                new RoomController(guests, rooms, socket),
                new DeckController(guests, imports, socket),
                new UnrelatedController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  private void assertJson(final String actual, final Object expected) throws Exception {
    assertThat(mapper.readTree(actual)).isEqualTo(mapper.valueToTree(expected));
  }

  @Test
  void sessionAndRoomReadRoutesKeepTheirShapes() throws Exception {
    when(guests.bootstrap(any(), any())).thenReturn(new GuestSessions.Guest(GUEST_ID, CSRF_TOKEN));
    when(rooms.list(GUEST_ID)).thenReturn(List.of(view));
    when(rooms.view(GUEST_ID, ROOM_ID)).thenReturn(view);
    assertJson(
        mvc.perform(get("/api/session")).andReturn().getResponse().getContentAsString(),
        Map.of(CSRF_TOKEN, CSRF_TOKEN));
    assertJson(
        mvc.perform(get(ROOMS_PATH)).andReturn().getResponse().getContentAsString(), List.of(view));
    assertJson(
        mvc.perform(get("/api/rooms/room")).andReturn().getResponse().getContentAsString(), view);
    verifyNoInteractions(socket);
  }

  @ParameterizedTest
  @CsvSource({
    "{},COMMANDER",
    "'{\"format\":null}',COMMANDER",
    "'{\"format\":\"STANDARD\"}',STANDARD",
    "'{\"format\":\"COMMANDER\"}',COMMANDER"
  })
  void creationDefaultsAndFormatsRemainCompatible(final String fields, final GameFormat format)
      throws Exception {
    when(rooms.create(GUEST_ID, null, format)).thenReturn(view);
    final MockHttpServletResponse result =
        mvc.perform(post(ROOMS_PATH).contentType(MediaType.APPLICATION_JSON).content(fields))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(200);
    assertJson(result.getContentAsString(), view);
    verify(rooms).create(GUEST_ID, null, format);
    verifyNoInteractions(socket);
  }

  @ParameterizedTest
  @CsvSource({
    "true,Invalid table format",
    "123,Invalid table format",
    "{},Invalid table format",
    "[],Invalid table format",
    "'\"standard\"',Choose COMMANDER or STANDARD",
    "'\"\"',Choose COMMANDER or STANDARD"
  })
  void invalidFormatPrecedesControllerGuestLookup(final String raw, final String message)
      throws Exception {
    when(guests.require(any())).thenThrow(new RoomStore.Forbidden("no guest"));
    final MockHttpServletResponse result =
        mvc.perform(
                post(ROOMS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"format\":" + raw + "}"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(400);
    assertJson(result.getContentAsString(), Map.of(MESSAGE_FIELD, message));
    verifyNoInteractions(guests, rooms, socket);
  }

  @Test
  void joinAndCommandsBroadcastAfterMutation() throws Exception {
    when(rooms.join(GUEST_ID, ROOM_ID, "Player")).thenReturn(view);
    when(rooms.execute(eq(GUEST_ID), eq(ROOM_ID), any())).thenReturn(view);
    final MockHttpServletResponse join =
        mvc.perform(
                post("/api/rooms/room/join")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Player\"}"))
            .andReturn()
            .getResponse();
    assertJson(join.getContentAsString(), view);
    final MockHttpServletResponse command =
        mvc.perform(
                post("/api/rooms/room/commands")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":\"cmd\",\"revision\":6,\"type\":\"DRAW\",\"data\":{}}"))
            .andReturn()
            .getResponse();
    assertJson(command.getContentAsString(), view);
    final InOrder order = inOrder(rooms, socket);
    order.verify(rooms).join(GUEST_ID, ROOM_ID, "Player");
    order.verify(socket).broadcast(ROOM_ID);
    order.verify(rooms).execute(eq(GUEST_ID), eq(ROOM_ID), any());
    order.verify(socket).broadcast(ROOM_ID);
  }

  @Test
  void previewRetainsOrderAndResponseWithoutBroadcast() throws Exception {
    when(importer.preview("deck", "url", GameFormat.COMMANDER))
        .thenReturn(new DeckImporter.Draft(List.of(card), List.of(card), List.of(CARD_ID)));
    final MockHttpServletResponse result =
        mvc.perform(
                post(PREVIEW_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"deck\",\"url\":\"url\"}"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(200);
    final JsonNode json = mapper.readTree(result.getContentAsString());
    UUID.fromString(json.path("draftId").asText());
    assertThat(json.properties())
        .extracting(Map.Entry::getKey)
        .containsExactlyInAnyOrder("draftId", "choices", "suggested", "count");
    assertThat(json.path("count").asInt()).isEqualTo(1);
    assertThat(json.path("choices")).isEqualTo(mapper.valueToTree(List.of(card)));
    assertThat(json.path("suggested")).isEqualTo(mapper.valueToTree(List.of(CARD_ID)));
    final String serialized = mapper.writeValueAsString(List.of(card));
    final InOrder order = inOrder(guests, rooms, importer, jdbc);
    order.verify(guests).require(any());
    order.verify(rooms).seat(GUEST_ID, ROOM_ID);
    order.verify(rooms).format(GUEST_ID, ROOM_ID);
    order.verify(importer).preview("deck", "url", GameFormat.COMMANDER);
    order.verify(jdbc).update(startsWith(DELETE_SQL), eq(GUEST_ID));
    order
        .verify(jdbc)
        .update(
            startsWith(INSERT_SQL),
            eq(json.path("draftId").asText()),
            eq(GUEST_ID),
            eq(serialized));
    verifyNoInteractions(socket);
  }

  @Test
  void unauthorizedPreviewStopsBeforeImportAndStorage() throws Exception {
    when(rooms.seat(GUEST_ID, ROOM_ID)).thenThrow(new RoomStore.Forbidden("No seat"));
    final MockHttpServletResponse result =
        mvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(403);
    verifyNoInteractions(importer, jdbc, socket);
    verify(rooms, never()).format(anyString(), anyString());
  }

  @Test
  void missingCommandersPrecedeDraftLookup() throws Exception {
    final MockHttpServletResponse result =
        mvc.perform(
                post(LOAD_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"draftId\":\"missing\"}"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(400);
    assertJson(result.getContentAsString(), Map.of(MESSAGE_FIELD, "Choose commanders"));
    verifyNoInteractions(jdbc, socket);
  }

  @Test
  void standardNullCommandersAndRepeatedLoadsRemainCompatible() throws Exception {
    when(rooms.format(GUEST_ID, ROOM_ID)).thenReturn(GameFormat.STANDARD);
    final String serialized = mapper.writeValueAsString(List.of(card));
    when(jdbc.queryForList(anyString(), eq(String.class), eq("draft"), eq(GUEST_ID)))
        .thenReturn(List.of(serialized));
    when(rooms.load(GUEST_ID, ROOM_ID, COMMAND_ID, 6, List.of(card), null)).thenReturn(view);
    for (int retry = 0; retry < 2; retry++) {
      final MockHttpServletResponse result =
          mvc.perform(
                  post(LOAD_PATH)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"draftId\":\"draft\",\"commandId\":\"cmd\",\"revision\":6}"))
              .andReturn()
              .getResponse();
      assertThat(result.getStatus()).isEqualTo(200);
      assertJson(result.getContentAsString(), view);
    }
    verify(rooms, times(2)).load(GUEST_ID, ROOM_ID, COMMAND_ID, 6, List.of(card), null);
    verify(socket, times(2)).broadcast(ROOM_ID);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void expiredDraftKeepsMessageAndDoesNotLoadOrBroadcast() throws Exception {
    when(jdbc.queryForList(anyString(), eq(String.class), eq("missing"), eq(GUEST_ID)))
        .thenReturn(List.of());
    final MockHttpServletResponse result =
        mvc.perform(
                post(LOAD_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"draftId\":\"missing\",\"commanders\":[\"card\"]}"))
            .andReturn()
            .getResponse();
    assertJson(
        result.getContentAsString(), Map.of(MESSAGE_FIELD, "Deck preview expired; preview again"));
    verify(rooms, never()).load(anyString(), anyString(), anyString(), anyLong(), anyList(), any());
    verifyNoInteractions(socket);
  }

  private void successfulImport() {
    when(importer.preview(any(), any(), any()))
        .thenReturn(new DeckImporter.Draft(List.of(card), List.of(card), List.of(CARD_ID)));
  }

  private void availableDraft() throws Exception {
    final String serialized = mapper.writeValueAsString(List.of(card));
    when(jdbc.queryForList(anyString(), eq(String.class), eq("draft"), eq(GUEST_ID)))
        .thenReturn(List.of(serialized));
  }

  private void assertGenericFailure(final String path, final String body) throws Exception {
    try (LogCapture logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      final MockHttpServletResponse result =
          mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
              .andReturn()
              .getResponse();
      assertThat(result.getStatus()).isEqualTo(500);
      assertJson(
          result.getContentAsString(),
          Map.of(
              MESSAGE_FIELD,
              "Request failed; saved state was not acknowledged. Refresh before retrying."));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
    }
  }

  @Test
  void previewSerializationFailureOccursAfterPurgeAndBeforeInsert() throws Exception {
    successfulImport();
    doThrow(new JsonProcessingException(FAILURE_MESSAGE) {}).when(mapper).writeValueAsString(any());
    assertGenericFailure(PREVIEW_PATH, "{}");
    verify(jdbc).update(startsWith(DELETE_SQL), eq(GUEST_ID));
    verify(jdbc, never()).update(startsWith(INSERT_SQL), any(), any(), any());
    verifyNoInteractions(socket);
  }

  @Test
  void previewPurgeFailureStopsBeforeSerializationAndInsert() throws Exception {
    successfulImport();
    when(jdbc.update(startsWith(DELETE_SQL), eq(GUEST_ID)))
        .thenThrow(new IllegalStateException(FAILURE_MESSAGE));
    assertGenericFailure(PREVIEW_PATH, "{}");
    verify(mapper, never()).writeValueAsString(any());
    verify(jdbc, never()).update(startsWith(INSERT_SQL), any(), any(), any());
    verifyNoInteractions(socket);
  }

  @Test
  void previewInsertFailureFollowsPurgeAndDoesNotBroadcast() throws Exception {
    successfulImport();
    when(jdbc.update(startsWith(INSERT_SQL), any(), eq(GUEST_ID), any()))
        .thenThrow(new IllegalStateException(FAILURE_MESSAGE));
    assertGenericFailure(PREVIEW_PATH, "{}");
    final InOrder order = inOrder(jdbc);
    order.verify(jdbc).update(startsWith(DELETE_SQL), eq(GUEST_ID));
    order.verify(jdbc).update(startsWith(INSERT_SQL), any(), eq(GUEST_ID), any());
    verifyNoInteractions(socket);
  }

  @Test
  void malformedStoredDraftStopsBeforeLoadAndBroadcast() throws Exception {
    when(jdbc.queryForList(anyString(), eq(String.class), eq("draft"), eq(GUEST_ID)))
        .thenReturn(List.of(FAILURE_MESSAGE));
    assertGenericFailure(LOAD_PATH, LOAD_BODY);
    verify(rooms, never()).load(anyString(), anyString(), anyString(), anyLong(), anyList(), any());
    verifyNoInteractions(socket);
  }

  @Test
  void roomLoadFailureDoesNotBroadcast() throws Exception {
    availableDraft();
    when(rooms.load(anyString(), anyString(), anyString(), anyLong(), anyList(), any()))
        .thenThrow(new IllegalStateException(FAILURE_MESSAGE));
    assertGenericFailure(LOAD_PATH, LOAD_BODY);
    verify(rooms).load(GUEST_ID, ROOM_ID, COMMAND_ID, 6, List.of(card), List.of(CARD_ID));
    verifyNoInteractions(socket);
  }

  @Test
  void broadcastFailureOccursAfterSuccessfulLoad() throws Exception {
    availableDraft();
    when(rooms.load(GUEST_ID, ROOM_ID, COMMAND_ID, 6, List.of(card), List.of(CARD_ID)))
        .thenReturn(view);
    doThrow(new IllegalStateException(FAILURE_MESSAGE)).when(socket).broadcast(ROOM_ID);
    assertGenericFailure(LOAD_PATH, LOAD_BODY);
    final InOrder order = inOrder(rooms, socket);
    order.verify(rooms).seat(GUEST_ID, ROOM_ID);
    order.verify(rooms).format(GUEST_ID, ROOM_ID);
    order.verify(rooms).load(GUEST_ID, ROOM_ID, COMMAND_ID, 6, List.of(card), List.of(CARD_ID));
    order.verify(socket).broadcast(ROOM_ID);
  }

  @ParameterizedTest
  @MethodSource("dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void serviceFailurePreserves400AndWarning(final CardServiceException failure) throws Exception {
    when(rooms.list(GUEST_ID)).thenThrow(failure);
    try (LogCapture logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      final MockHttpServletResponse result = mvc.perform(get(ROOMS_PATH)).andReturn().getResponse();
      assertThat(result.getStatus()).isEqualTo(400);
      assertJson(result.getContentAsString(), Map.of(MESSAGE_FIELD, failure.getMessage()));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.WARN);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "conflict,409,Table changed; refresh and retry",
    "forbidden,403,denied",
    "invalid,400,bad input"
  })
  void expectedFailuresDoNotBroadcastOrLog(
      final String kind, final int status, final String message) throws Exception {
    final RuntimeException failure =
        switch (kind) {
          case "conflict" -> new RoomStore.Conflict();
          case "forbidden" -> new RoomStore.Forbidden(message);
          default -> new IllegalArgumentException(message);
        };
    when(rooms.join(anyString(), anyString(), anyString())).thenThrow(failure);
    try (LogCapture logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      final MockHttpServletResponse result =
          mvc.perform(
                  post("/api/rooms/room/join")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"name\":\"Player\"}"))
              .andReturn()
              .getResponse();
      assertThat(result.getStatus()).isEqualTo(status);
      assertJson(result.getContentAsString(), Map.of(MESSAGE_FIELD, message));
      assertThat(logs.events()).isEmpty();
    }
    verifyNoInteractions(socket);
  }

  @Test
  void malformedBodyRetainsExistingGeneric500Response() throws Exception {
    final MockHttpServletResponse result =
        mvc.perform(post(ROOMS_PATH).contentType(MediaType.APPLICATION_JSON).content("{broken"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(500);
    verifyNoInteractions(guests, rooms, socket);
  }

  @org.springframework.web.bind.annotation.RestController
  static class UnrelatedController {
    @org.springframework.web.bind.annotation.GetMapping("/unrelated")
    public String fail() {
      throw new RoomStore.Forbidden("unrelated");
    }
  }

  @Test
  void unrelatedControllersKeepTheirOwnErrorPolicy() {
    assertThatThrownBy(() -> mvc.perform(get("/unrelated")))
        .hasRootCauseInstanceOf(RoomStore.Forbidden.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"session", "rooms", "decks"})
  void unexpectedErrorsUseTheSamePolicyAcrossEndpointGroups(final String group) throws Exception {
    final IllegalStateException failure = new IllegalStateException(FAILURE_MESSAGE);
    if ("session".equals(Objects.requireNonNull(group))) {
      when(guests.bootstrap(any(), any())).thenThrow(failure);
    }
    if ("rooms".equals(Objects.requireNonNull(group))) {
      when(rooms.list(GUEST_ID)).thenThrow(failure);
    }
    if ("decks".equals(Objects.requireNonNull(group))) {
      when(importer.preview(any(), any(), any())).thenThrow(failure);
    }
    try (LogCapture logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      final MockHttpServletResponse result =
          "decks".equals(Objects.requireNonNull(group))
              ? mvc.perform(
                      post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                  .andReturn()
                  .getResponse()
              : mvc.perform(get("/api/" + group)).andReturn().getResponse();
      assertThat(result.getStatus()).isEqualTo(500);
      assertJson(
          result.getContentAsString(),
          Map.of(
              MESSAGE_FIELD,
              "Request failed; saved state was not acknowledged. Refresh before retrying."));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
    }
  }
}
