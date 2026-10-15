package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.catalog.CardServiceException;
import dev.mtgcmd.game.Game;
import dev.mtgcmd.store.RoomStore;
import dev.mtgcmd.testsupport.LogCapture;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class DiagnosticsTest {
  private static final String DATABASE_ERROR = "database unavailable";

  private static final String GUEST_ATTRIBUTE = "guest";
  private static final String ROOM_ATTRIBUTE = "room";

  private static final String GUEST_ID = "guest";

  private static final String ROOM_ID = "room";

  private record SocketFixture(
      RoomStore rooms, ObjectMapper mapper, GameSocket socket, WebSocketSession session) {}

  private SocketFixture socketFixture() throws Exception {
    final RoomStore rooms = mock(RoomStore.class);
    final ObjectMapper mapper = spy(new ObjectMapper());
    final GameSocket socket = new GameSocket(mapper, rooms);
    // Mock session allocates no connection; closure interactions are asserted by lifecycle tests.
    final WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("connection");
    when(session.getAttributes())
        .thenReturn(Map.of(GUEST_ATTRIBUTE, GUEST_ID, ROOM_ATTRIBUTE, ROOM_ID, "seat", 0));
    when(session.isOpen()).thenReturn(true);
    when(rooms.view(GUEST_ID, ROOM_ID)).thenReturn(Map.of());
    socket.afterConnectionEstablished(session);
    clearInvocations(session);
    return new SocketFixture(rooms, mapper, socket, session);
  }

  @ParameterizedTest
  @MethodSource("dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void typedHttpFailuresAreWarnedOnceAndKeepTheExistingResponse(final CardServiceException failure)
      throws Exception {
    final GuestSessions guests = mock(GuestSessions.class);
    final RoomStore rooms = mock(RoomStore.class);
    when(guests.require(any())).thenReturn(new GuestSessions.Guest(GUEST_ID, "csrf"));
    when(rooms.list(GUEST_ID)).thenThrow(failure);
    final RoomController api = new RoomController(guests, rooms, null);
    final MockMvc mvc =
        MockMvcBuilders.standaloneSetup(api).setControllerAdvice(new ApiExceptionHandler()).build();
    try (LogCapture logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      final MockHttpServletResponse response =
          mvc.perform(get("/api/rooms")).andReturn().getResponse();
      assertThat(response.getStatus()).isEqualTo(400);
      assertThat(new ObjectMapper().readTree(response.getContentAsString()))
          .isEqualTo(new ObjectMapper().valueToTree(Map.of("message", failure.getMessage())));
      assertThat(logs.events()).hasSize(1);
      final ILoggingEvent event = logs.events().getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getThrowableProxy().getClassName()).isEqualTo(failure.getClass().getName());
      assertThat(event.getThrowableProxy().getCause().getClassName())
          .isEqualTo(failure.getCause().getClass().getName());
    }
  }

  @ParameterizedTest
  @MethodSource("dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void typedSocketFailuresAreWarnedOnceAndKeepTheirMessage(final CardServiceException failure)
      throws Exception {
    final SocketFixture fixture = socketFixture();
    when(fixture.rooms().execute(eq(GUEST_ID), eq(ROOM_ID), any(Game.Command.class)))
        .thenThrow(failure);
    try (LogCapture logs = new LogCapture(GameSocket.class)) {
      fixture
          .socket()
          .handleTextMessage(
              fixture.session(),
              new TextMessage("{\"id\":\"command\",\"revision\":0,\"type\":\"DRAW\",\"data\":{}}"));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.WARN);
      assertThat(logs.events().getFirst().getThrowableProxy().getClassName())
          .isEqualTo(failure.getClass().getName());
      verify(fixture.session())
          .sendMessage(
              argThat(
                  message ->
                      message instanceof TextMessage text
                          && text.getPayload().contains(failure.getMessage())));
    }
  }

  @Test
  void ordinarySocketValidationIsQuietAndFailedViewRecoveryIsObservable() throws Exception {
    final SocketFixture fixture = socketFixture();
    when(fixture.rooms().execute(eq(GUEST_ID), eq(ROOM_ID), any(Game.Command.class)))
        .thenThrow(new IllegalArgumentException("Not enough cards in library"));
    final TextMessage command =
        new TextMessage("{\"id\":\"command\",\"revision\":0,\"type\":\"DRAW\",\"data\":{}}");
    try (LogCapture logs = new LogCapture(GameSocket.class)) {
      fixture.socket().handleTextMessage(fixture.session(), command);
      assertThat(logs.events()).isEmpty();
      when(fixture.rooms().view(GUEST_ID, ROOM_ID))
          .thenThrow(new IllegalStateException(DATABASE_ERROR));
      fixture.socket().handleTextMessage(fixture.session(), command);
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
      assertThat(logs.events().getFirst().getFormattedMessage()).contains("recovery");
    }
  }

  @Test
  void snapshotFailureProducesOneErrorAndRetainsItsGenericResponse() throws Exception {
    final SocketFixture fixture = socketFixture();
    when(fixture.rooms().view(GUEST_ID, ROOM_ID))
        .thenThrow(new IllegalStateException(DATABASE_ERROR));
    try (LogCapture logs = new LogCapture(GameSocket.class)) {
      fixture.socket().broadcast(ROOM_ID);
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
      verify(fixture.session())
          .sendMessage(
              argThat(
                  message ->
                      message instanceof TextMessage text
                          && text.getPayload().contains("Game temporarily unavailable")));
    }
  }

  @Test
  void sendFailureRecordsOriginalAndCleanupFailuresWithoutDuplicateLogging() throws Exception {
    final SocketFixture fixture = socketFixture();
    final IOException sendFailure = new IOException("send failed");
    final IOException closeFailure = new IOException("close failed");
    doThrow(sendFailure).when(fixture.session()).sendMessage(any());
    doThrow(closeFailure).when(fixture.session()).close(CloseStatus.SERVER_ERROR);
    try (LogCapture logs = new LogCapture(GameSocket.class)) {
      fixture.socket().broadcast(ROOM_ID);
      assertThat(logs.events()).hasSize(1);
      final ILoggingEvent event = logs.events().getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getThrowableProxy().getMessage()).isEqualTo("send failed");
      assertThat(event.getThrowableProxy().getSuppressed()).hasSize(1);
      assertThat(sendFailure.getSuppressed()).containsExactly(closeFailure);
      verify(fixture.session()).close(CloseStatus.SERVER_ERROR);
    }
  }

  @Test
  void serializationFailureIsAnErrorAndClosesTheConnection() throws Exception {
    final SocketFixture fixture = socketFixture();
    doThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialization failed") {})
        .when(fixture.mapper())
        .writeValueAsString(any());
    try (LogCapture logs = new LogCapture(GameSocket.class)) {
      fixture.socket().broadcast(ROOM_ID);
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
      verify(fixture.session()).close(CloseStatus.SERVER_ERROR);
    }
  }

  @Test
  void transportFailureIsLoggedAndConnectionClosed() throws Exception {
    final SocketFixture fixture = socketFixture();
    final IOException failure = new IOException("transport failed");
    try (LogCapture logs = new LogCapture(GameSocket.class)) {
      fixture.socket().handleTransportError(fixture.session(), failure);
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.WARN);
      verify(fixture.session()).close(CloseStatus.SERVER_ERROR);
    }
  }

  @Test
  void failedTransportCleanupRetainsTheOriginalFailureAndPropagatesTheCloseFailure()
      throws Exception {
    final SocketFixture fixture = socketFixture();
    final IOException transportFailure = new IOException("transport failed");
    final IOException closeFailure = new IOException("close failed");
    doThrow(closeFailure).when(fixture.session()).close(CloseStatus.SERVER_ERROR);
    try (LogCapture logs = new LogCapture(GameSocket.class)) {
      assertThat(
              catchThrowable(
                  () -> fixture.socket().handleTransportError(fixture.session(), transportFailure)))
          .isSameAs(closeFailure);
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getThrowableProxy().getMessage())
          .isEqualTo("transport failed");
      assertThat(transportFailure.getSuppressed()).containsExactly(closeFailure);
    }
  }

  @Test
  void unexpectedHttpFailureIsLoggedOnceAndKeepsItsGenericResponse() {
    final ApiExceptionHandler api = new ApiExceptionHandler();
    final IllegalStateException failure = new IllegalStateException(DATABASE_ERROR);
    try (LogCapture logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      final ResponseEntity<?> response = api.failed(failure);
      assertThat(response.getStatusCode().value()).isEqualTo(500);
      assertThat(response.getBody())
          .isEqualTo(
              Map.of(
                  "message",
                  "Request failed; saved state was not acknowledged. Refresh before retrying."));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
      assertThat(logs.events().getFirst().getThrowableProxy().getClassName())
          .isEqualTo(IllegalStateException.class.getName());
    }
  }

  @Test
  void ordinaryHttpValidationDoesNotProduceFailureLogs() {
    final ApiExceptionHandler api = new ApiExceptionHandler();
    try (LogCapture logs = new LogCapture(ApiExceptionHandler.LOGGER_NAME)) {
      final ResponseEntity<?> response =
          api.invalid(new IllegalArgumentException("Choose commanders"));
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(response.getBody()).isEqualTo(Map.of("message", "Choose commanders"));
      assertThat(logs.events()).isEmpty();
    }
  }

  @Test
  void unexpectedSocketFailureIsLoggedAndKeepsItsGenericResponse() throws Exception {
    final RoomStore rooms = mock(RoomStore.class);
    final ObjectMapper mapper = new ObjectMapper();
    final GameSocket socket = new GameSocket(mapper, rooms);
    // Mock session allocates no connection; closure interactions are asserted by lifecycle tests.
    @SuppressWarnings("PMD.CloseResource")
    final WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("connection");
    when(session.getAttributes())
        .thenReturn(Map.of(GUEST_ATTRIBUTE, GUEST_ID, ROOM_ATTRIBUTE, ROOM_ID, "seat", 0));
    when(session.isOpen()).thenReturn(true);
    when(rooms.view(GUEST_ID, ROOM_ID)).thenReturn(Map.of());
    socket.afterConnectionEstablished(session);
    clearInvocations(session);
    when(rooms.execute(eq(GUEST_ID), eq(ROOM_ID), any(Game.Command.class)))
        .thenThrow(new IllegalStateException(DATABASE_ERROR));
    try (LogCapture logs = new LogCapture(GameSocket.class)) {
      socket.handleTextMessage(
          session,
          new TextMessage("{\"id\":\"command\",\"revision\":0,\"type\":\"DRAW\",\"data\":{}}"));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
      assertThat(logs.events().getFirst().getFormattedMessage()).contains(ROOM_ID);
      verify(session)
          .sendMessage(
              argThat(
                  message ->
                      message instanceof TextMessage text
                          && text.getPayload()
                              .contains("Action failed. Refresh before retrying.")));
    }
  }

  @Test
  void normalSocketClosureRemovesRegistryEntryWithoutPrematureClosure() throws Exception {
    final SocketFixture fixture = socketFixture();
    verify(fixture.session(), never()).close(any(CloseStatus.class));
    fixture.socket().broadcast(ROOM_ID);
    verify(fixture.session()).sendMessage(any());
    clearInvocations(fixture.session());
    fixture.socket().afterConnectionClosed(fixture.session(), CloseStatus.NORMAL);
    fixture.socket().broadcast(ROOM_ID);
    verify(fixture.session(), never()).sendMessage(any());
    verify(fixture.rooms(), times(2)).view(GUEST_ID, ROOM_ID);
  }
}
