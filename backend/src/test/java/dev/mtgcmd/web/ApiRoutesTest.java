package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.catalog.*;
import dev.mtgcmd.game.Game;
import dev.mtgcmd.game.GameFormat;
import dev.mtgcmd.store.RoomStore;
import dev.mtgcmd.testsupport.LogCapture;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiRoutesTest {
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
      new Game.Definition("card", "Card", "", "", "Legendary Creature", List.of(), true, List.of());
  private final Map<String, Object> view = Map.of("revision", 7, "you", 0);

  @BeforeEach
  void setup() {
    when(guests.require(any())).thenReturn(new GuestSessions.Guest("guest", "csrf"));
    when(rooms.format("guest", "room")).thenReturn(GameFormat.COMMANDER);
    var drafts = new dev.mtgcmd.store.DeckDraftRepository(jdbc, mapper);
    var imports = new DeckImportService(rooms, importer, drafts);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new SessionController(guests),
                new RoomController(guests, rooms, socket),
                new DeckController(guests, imports, socket),
                new UnrelatedController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  private void assertJson(String actual, Object expected) throws Exception {
    assertThat(mapper.readTree(actual)).isEqualTo(mapper.valueToTree(expected));
  }

  @Test
  void sessionAndRoomReadRoutesKeepTheirShapes() throws Exception {
    when(guests.bootstrap(any(), any())).thenReturn(new GuestSessions.Guest("guest", "csrf"));
    when(rooms.list("guest")).thenReturn(List.of(view));
    when(rooms.view("guest", "room")).thenReturn(view);
    assertJson(
        mvc.perform(get("/api/session")).andReturn().getResponse().getContentAsString(),
        Map.of("csrf", "csrf"));
    assertJson(
        mvc.perform(get("/api/rooms")).andReturn().getResponse().getContentAsString(),
        List.of(view));
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
  void creationDefaultsAndFormatsRemainCompatible(String fields, GameFormat format)
      throws Exception {
    when(rooms.create("guest", null, format)).thenReturn(view);
    var result =
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(fields))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(200);
    assertJson(result.getContentAsString(), view);
    verify(rooms).create("guest", null, format);
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
  void invalidFormatPrecedesControllerGuestLookup(String raw, String message) throws Exception {
    when(guests.require(any())).thenThrow(new RoomStore.Forbidden("no guest"));
    var result =
        mvc.perform(
                post("/api/rooms")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"format\":" + raw + "}"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(400);
    assertJson(result.getContentAsString(), Map.of("message", message));
    verifyNoInteractions(guests, rooms, socket);
  }

  @Test
  void joinAndCommandsBroadcastAfterMutation() throws Exception {
    when(rooms.join("guest", "room", "Player")).thenReturn(view);
    when(rooms.execute(eq("guest"), eq("room"), any())).thenReturn(view);
    var join =
        mvc.perform(
                post("/api/rooms/room/join")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Player\"}"))
            .andReturn()
            .getResponse();
    assertJson(join.getContentAsString(), view);
    var command =
        mvc.perform(
                post("/api/rooms/room/commands")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":\"cmd\",\"revision\":6,\"type\":\"DRAW\",\"data\":{}}"))
            .andReturn()
            .getResponse();
    assertJson(command.getContentAsString(), view);
    var order = inOrder(rooms, socket);
    order.verify(rooms).join("guest", "room", "Player");
    order.verify(socket).broadcast("room");
    order.verify(rooms).execute(eq("guest"), eq("room"), any());
    order.verify(socket).broadcast("room");
  }

  @Test
  void previewRetainsOrderAndResponseWithoutBroadcast() throws Exception {
    when(importer.preview("deck", "url", GameFormat.COMMANDER))
        .thenReturn(new DeckImporter.Draft(List.of(card), List.of(card), List.of("card")));
    var result =
        mvc.perform(
                post("/api/rooms/room/decks/preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"deck\",\"url\":\"url\"}"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(200);
    var json = mapper.readTree(result.getContentAsString());
    UUID.fromString(json.path("draftId").asText());
    assertThat(json.properties())
        .extracting(Map.Entry::getKey)
        .containsExactlyInAnyOrder("draftId", "choices", "suggested", "count");
    assertThat(json.path("count").asInt()).isEqualTo(1);
    assertThat(json.path("choices")).isEqualTo(mapper.valueToTree(List.of(card)));
    assertThat(json.path("suggested")).isEqualTo(mapper.valueToTree(List.of("card")));
    String serialized = mapper.writeValueAsString(List.of(card));
    var order = inOrder(guests, rooms, importer, jdbc);
    order.verify(guests).require(any());
    order.verify(rooms).seat("guest", "room");
    order.verify(rooms).format("guest", "room");
    order.verify(importer).preview("deck", "url", GameFormat.COMMANDER);
    order.verify(jdbc).update(startsWith("DELETE"), eq("guest"));
    order
        .verify(jdbc)
        .update(
            startsWith("INSERT"), eq(json.path("draftId").asText()), eq("guest"), eq(serialized));
    verifyNoInteractions(socket);
  }

  @Test
  void unauthorizedPreviewStopsBeforeImportAndStorage() throws Exception {
    when(rooms.seat("guest", "room")).thenThrow(new RoomStore.Forbidden("No seat"));
    var result =
        mvc.perform(
                post("/api/rooms/room/decks/preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(403);
    verifyNoInteractions(importer, jdbc, socket);
    verify(rooms, never()).format(anyString(), anyString());
  }

  @Test
  void missingCommandersPrecedeDraftLookup() throws Exception {
    var result =
        mvc.perform(
                post("/api/rooms/room/deck")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"draftId\":\"missing\"}"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(400);
    assertJson(result.getContentAsString(), Map.of("message", "Choose commanders"));
    verifyNoInteractions(jdbc, socket);
  }

  @Test
  void standardNullCommandersAndRepeatedLoadsRemainCompatible() throws Exception {
    when(rooms.format("guest", "room")).thenReturn(GameFormat.STANDARD);
    String serialized = mapper.writeValueAsString(List.of(card));
    when(jdbc.queryForList(anyString(), eq(String.class), eq("draft"), eq("guest")))
        .thenReturn(List.of(serialized));
    when(rooms.load("guest", "room", "cmd", 6, List.of(card), null)).thenReturn(view);
    for (int retry = 0; retry < 2; retry++) {
      var result =
          mvc.perform(
                  post("/api/rooms/room/deck")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"draftId\":\"draft\",\"commandId\":\"cmd\",\"revision\":6}"))
              .andReturn()
              .getResponse();
      assertThat(result.getStatus()).isEqualTo(200);
      assertJson(result.getContentAsString(), view);
    }
    verify(rooms, times(2)).load("guest", "room", "cmd", 6, List.of(card), null);
    verify(socket, times(2)).broadcast("room");
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void expiredDraftKeepsMessageAndDoesNotLoadOrBroadcast() throws Exception {
    when(jdbc.queryForList(anyString(), eq(String.class), eq("missing"), eq("guest")))
        .thenReturn(List.of());
    var result =
        mvc.perform(
                post("/api/rooms/room/deck")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"draftId\":\"missing\",\"commanders\":[\"card\"]}"))
            .andReturn()
            .getResponse();
    assertJson(
        result.getContentAsString(), Map.of("message", "Deck preview expired; preview again"));
    verify(rooms, never()).load(anyString(), anyString(), anyString(), anyLong(), anyList(), any());
    verifyNoInteractions(socket);
  }

  private void successfulImport() {
    when(importer.preview(any(), any(), any()))
        .thenReturn(new DeckImporter.Draft(List.of(card), List.of(card), List.of("card")));
  }

  private void availableDraft() throws Exception {
    String serialized = mapper.writeValueAsString(List.of(card));
    when(jdbc.queryForList(anyString(), eq(String.class), eq("draft"), eq("guest")))
        .thenReturn(List.of(serialized));
  }

  private void assertGenericFailure(String path, String body) throws Exception {
    try (var logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      var result =
          mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
              .andReturn()
              .getResponse();
      assertThat(result.getStatus()).isEqualTo(500);
      assertJson(
          result.getContentAsString(),
          Map.of(
              "message",
              "Request failed; saved state was not acknowledged. Refresh before retrying."));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
    }
  }

  @Test
  void previewSerializationFailureOccursAfterPurgeAndBeforeInsert() throws Exception {
    successfulImport();
    doThrow(new JsonProcessingException("broken") {}).when(mapper).writeValueAsString(any());
    assertGenericFailure(PREVIEW_PATH, "{}");
    verify(jdbc).update(startsWith("DELETE"), eq("guest"));
    verify(jdbc, never()).update(startsWith("INSERT"), any(), any(), any());
    verifyNoInteractions(socket);
  }

  @Test
  void previewPurgeFailureStopsBeforeSerializationAndInsert() throws Exception {
    successfulImport();
    when(jdbc.update(startsWith("DELETE"), eq("guest")))
        .thenThrow(new IllegalStateException("broken"));
    assertGenericFailure(PREVIEW_PATH, "{}");
    verify(mapper, never()).writeValueAsString(any());
    verify(jdbc, never()).update(startsWith("INSERT"), any(), any(), any());
    verifyNoInteractions(socket);
  }

  @Test
  void previewInsertFailureFollowsPurgeAndDoesNotBroadcast() throws Exception {
    successfulImport();
    when(jdbc.update(startsWith("INSERT"), any(), eq("guest"), any()))
        .thenThrow(new IllegalStateException("broken"));
    assertGenericFailure(PREVIEW_PATH, "{}");
    var order = inOrder(jdbc);
    order.verify(jdbc).update(startsWith("DELETE"), eq("guest"));
    order.verify(jdbc).update(startsWith("INSERT"), any(), eq("guest"), any());
    verifyNoInteractions(socket);
  }

  @Test
  void malformedStoredDraftStopsBeforeLoadAndBroadcast() throws Exception {
    when(jdbc.queryForList(anyString(), eq(String.class), eq("draft"), eq("guest")))
        .thenReturn(List.of("broken"));
    assertGenericFailure(LOAD_PATH, LOAD_BODY);
    verify(rooms, never()).load(anyString(), anyString(), anyString(), anyLong(), anyList(), any());
    verifyNoInteractions(socket);
  }

  @Test
  void roomLoadFailureDoesNotBroadcast() throws Exception {
    availableDraft();
    when(rooms.load(anyString(), anyString(), anyString(), anyLong(), anyList(), any()))
        .thenThrow(new IllegalStateException("broken"));
    assertGenericFailure(LOAD_PATH, LOAD_BODY);
    verify(rooms).load("guest", "room", "cmd", 6, List.of(card), List.of("card"));
    verifyNoInteractions(socket);
  }

  @Test
  void broadcastFailureOccursAfterSuccessfulLoad() throws Exception {
    availableDraft();
    when(rooms.load("guest", "room", "cmd", 6, List.of(card), List.of("card"))).thenReturn(view);
    doThrow(new IllegalStateException("broken")).when(socket).broadcast("room");
    assertGenericFailure(LOAD_PATH, LOAD_BODY);
    var order = inOrder(rooms, socket);
    order.verify(rooms).seat("guest", "room");
    order.verify(rooms).format("guest", "room");
    order.verify(rooms).load("guest", "room", "cmd", 6, List.of(card), List.of("card"));
    order.verify(socket).broadcast("room");
  }

  @ParameterizedTest
  @MethodSource("dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void serviceFailurePreserves400AndWarning(CardServiceException failure) throws Exception {
    when(rooms.list("guest")).thenThrow(failure);
    try (var logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      var result = mvc.perform(get("/api/rooms")).andReturn().getResponse();
      assertThat(result.getStatus()).isEqualTo(400);
      assertJson(result.getContentAsString(), Map.of("message", failure.getMessage()));
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
  void expectedFailuresDoNotBroadcastOrLog(String kind, int status, String message)
      throws Exception {
    RuntimeException failure =
        switch (kind) {
          case "conflict" -> new RoomStore.Conflict();
          case "forbidden" -> new RoomStore.Forbidden(message);
          default -> new IllegalArgumentException(message);
        };
    when(rooms.join(anyString(), anyString(), anyString())).thenThrow(failure);
    try (var logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      var result =
          mvc.perform(
                  post("/api/rooms/room/join")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"name\":\"Player\"}"))
              .andReturn()
              .getResponse();
      assertThat(result.getStatus()).isEqualTo(status);
      assertJson(result.getContentAsString(), Map.of("message", message));
      assertThat(logs.events()).isEmpty();
    }
    verifyNoInteractions(socket);
  }

  @Test
  void malformedBodyRetainsExistingGeneric500Response() throws Exception {
    var result =
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content("{broken"))
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
  void unexpectedErrorsUseTheSamePolicyAcrossEndpointGroups(String group) throws Exception {
    var failure = new IllegalStateException("broken");
    if (group.equals("session")) when(guests.bootstrap(any(), any())).thenThrow(failure);
    if (group.equals("rooms")) when(rooms.list("guest")).thenThrow(failure);
    if (group.equals("decks")) when(importer.preview(any(), any(), any())).thenThrow(failure);
    try (var logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      var result =
          group.equals("decks")
              ? mvc.perform(
                      post("/api/rooms/room/decks/preview")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{}"))
                  .andReturn()
                  .getResponse()
              : mvc.perform(get("/api/" + group)).andReturn().getResponse();
      assertThat(result.getStatus()).isEqualTo(500);
      assertJson(
          result.getContentAsString(),
          Map.of(
              "message",
              "Request failed; saved state was not acknowledged. Refresh before retrying."));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
    }
  }
}
