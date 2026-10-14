package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class DiagnosticsTest {
  private record SocketFixture(
      RoomStore rooms, ObjectMapper mapper, GameSocket socket, WebSocketSession session) {}

  private SocketFixture socketFixture() throws Exception {
    var rooms = mock(RoomStore.class);
    var mapper = spy(new ObjectMapper());
    var socket = new GameSocket(mapper, rooms);
    // Mock session allocates no connection; closure interactions are asserted by lifecycle tests.
    @SuppressWarnings("PMD.CloseResource")
    var session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("connection");
    when(session.getAttributes()).thenReturn(Map.of("guest", "guest", "room", "room", "seat", 0));
    when(session.isOpen()).thenReturn(true);
    when(rooms.view("guest", "room")).thenReturn(Map.of());
    socket.afterConnectionEstablished(session);
    clearInvocations(session);
    return new SocketFixture(rooms, mapper, socket, session);
  }

  @ParameterizedTest
  @MethodSource("dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void typedHttpFailuresAreWarnedOnceAndKeepTheExistingResponse(CardServiceException failure)
      throws Exception {
    var guests = mock(GuestSessions.class);
    var rooms = mock(RoomStore.class);
    when(guests.require(any())).thenReturn(new GuestSessions.Guest("guest", "csrf"));
    when(rooms.list("guest")).thenThrow(failure);
    var api = new ApiController(guests, rooms, null, null, null, null);
    var mvc = MockMvcBuilders.standaloneSetup(api).build();
    try (var logs = new LogCapture(ApiController.class)) {
      var response = mvc.perform(get("/api/rooms")).andReturn().getResponse();
      assertThat(response.getStatus()).isEqualTo(400);
      assertThat(new ObjectMapper().readTree(response.getContentAsString()))
          .isEqualTo(new ObjectMapper().valueToTree(Map.of("message", failure.getMessage())));
      assertThat(logs.events()).hasSize(1);
      var event = logs.events().getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getThrowableProxy().getClassName()).isEqualTo(failure.getClass().getName());
      assertThat(event.getThrowableProxy().getCause().getClassName())
          .isEqualTo(failure.getCause().getClass().getName());
    }
  }

  @ParameterizedTest
  @MethodSource("dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void typedSocketFailuresAreWarnedOnceAndKeepTheirMessage(CardServiceException failure)
      throws Exception {
    var f = socketFixture();
    when(f.rooms().execute(eq("guest"), eq("room"), any(Game.Command.class))).thenThrow(failure);
    try (var logs = new LogCapture(GameSocket.class)) {
      f.socket()
          .handleTextMessage(
              f.session(),
              new TextMessage("{\"id\":\"command\",\"revision\":0,\"type\":\"DRAW\",\"data\":{}}"));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.WARN);
      assertThat(logs.events().getFirst().getThrowableProxy().getClassName())
          .isEqualTo(failure.getClass().getName());
      verify(f.session())
          .sendMessage(
              argThat(
                  message ->
                      message instanceof TextMessage text
                          && text.getPayload().contains(failure.getMessage())));
    }
  }

  @Test
  void ordinarySocketValidationIsQuietAndFailedViewRecoveryIsObservable() throws Exception {
    var f = socketFixture();
    when(f.rooms().execute(eq("guest"), eq("room"), any(Game.Command.class)))
        .thenThrow(new IllegalArgumentException("Not enough cards in library"));
    var command =
        new TextMessage("{\"id\":\"command\",\"revision\":0,\"type\":\"DRAW\",\"data\":{}}");
    try (var logs = new LogCapture(GameSocket.class)) {
      f.socket().handleTextMessage(f.session(), command);
      assertThat(logs.events()).isEmpty();
      when(f.rooms().view("guest", "room"))
          .thenThrow(new IllegalStateException("database unavailable"));
      f.socket().handleTextMessage(f.session(), command);
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
      assertThat(logs.events().getFirst().getFormattedMessage()).contains("recovery");
    }
  }

  @Test
  void snapshotFailureProducesOneErrorAndRetainsItsGenericResponse() throws Exception {
    var f = socketFixture();
    when(f.rooms().view("guest", "room"))
        .thenThrow(new IllegalStateException("database unavailable"));
    try (var logs = new LogCapture(GameSocket.class)) {
      f.socket().broadcast("room");
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
      verify(f.session())
          .sendMessage(
              argThat(
                  message ->
                      message instanceof TextMessage text
                          && text.getPayload().contains("Game temporarily unavailable")));
    }
  }

  @Test
  void sendFailureRecordsOriginalAndCleanupFailuresWithoutDuplicateLogging() throws Exception {
    var f = socketFixture();
    var sendFailure = new IOException("send failed");
    var closeFailure = new IOException("close failed");
    doThrow(sendFailure).when(f.session()).sendMessage(any());
    doThrow(closeFailure).when(f.session()).close(CloseStatus.SERVER_ERROR);
    try (var logs = new LogCapture(GameSocket.class)) {
      f.socket().broadcast("room");
      assertThat(logs.events()).hasSize(1);
      var event = logs.events().getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getThrowableProxy().getMessage()).isEqualTo("send failed");
      assertThat(event.getThrowableProxy().getSuppressed()).hasSize(1);
      assertThat(sendFailure.getSuppressed()).containsExactly(closeFailure);
      verify(f.session()).close(CloseStatus.SERVER_ERROR);
    }
  }

  @Test
  void serializationFailureIsAnErrorAndClosesTheConnection() throws Exception {
    var f = socketFixture();
    doThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialization failed") {})
        .when(f.mapper())
        .writeValueAsString(any());
    try (var logs = new LogCapture(GameSocket.class)) {
      f.socket().broadcast("room");
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
      verify(f.session()).close(CloseStatus.SERVER_ERROR);
    }
  }

  @Test
  void transportFailureIsLoggedAndConnectionClosed() throws Exception {
    var f = socketFixture();
    var failure = new IOException("transport failed");
    try (var logs = new LogCapture(GameSocket.class)) {
      f.socket().handleTransportError(f.session(), failure);
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.WARN);
      verify(f.session()).close(CloseStatus.SERVER_ERROR);
    }
  }

  @Test
  void failedTransportCleanupRetainsTheOriginalFailureAndPropagatesTheCloseFailure()
      throws Exception {
    var f = socketFixture();
    var transportFailure = new IOException("transport failed");
    var closeFailure = new IOException("close failed");
    doThrow(closeFailure).when(f.session()).close(CloseStatus.SERVER_ERROR);
    try (var logs = new LogCapture(GameSocket.class)) {
      assertThat(
              catchThrowable(() -> f.socket().handleTransportError(f.session(), transportFailure)))
          .isSameAs(closeFailure);
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getThrowableProxy().getMessage())
          .isEqualTo("transport failed");
      assertThat(transportFailure.getSuppressed()).containsExactly(closeFailure);
    }
  }

  @Test
  void unexpectedHttpFailureIsLoggedOnceAndKeepsItsGenericResponse() {
    var api = new ApiController(null, null, null, null, null, null);
    var failure = new IllegalStateException("database unavailable");
    try (var logs = new LogCapture(ApiController.class)) {
      var response = api.failed(failure);
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
    var api = new ApiController(null, null, null, null, null, null);
    try (var logs = new LogCapture(ApiController.class)) {
      var response = api.invalid(new IllegalArgumentException("Choose commanders"));
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(response.getBody()).isEqualTo(Map.of("message", "Choose commanders"));
      assertThat(logs.events()).isEmpty();
    }
  }

  @Test
  void unexpectedSocketFailureIsLoggedAndKeepsItsGenericResponse() throws Exception {
    var rooms = mock(RoomStore.class);
    var mapper = new ObjectMapper();
    var socket = new GameSocket(mapper, rooms);
    // Mock session allocates no connection; closure interactions are asserted by lifecycle tests.
    @SuppressWarnings("PMD.CloseResource")
    var session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("connection");
    when(session.getAttributes()).thenReturn(Map.of("guest", "guest", "room", "room", "seat", 0));
    when(session.isOpen()).thenReturn(true);
    when(rooms.view("guest", "room")).thenReturn(Map.of());
    socket.afterConnectionEstablished(session);
    clearInvocations(session);
    when(rooms.execute(eq("guest"), eq("room"), any(Game.Command.class)))
        .thenThrow(new IllegalStateException("database unavailable"));
    try (var logs = new LogCapture(GameSocket.class)) {
      socket.handleTextMessage(
          session,
          new TextMessage("{\"id\":\"command\",\"revision\":0,\"type\":\"DRAW\",\"data\":{}}"));
      assertThat(logs.events()).hasSize(1);
      assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.ERROR);
      assertThat(logs.events().getFirst().getFormattedMessage()).contains("room");
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
    var fixture = socketFixture();
    verify(fixture.session(), never()).close(any(CloseStatus.class));
    fixture.socket().broadcast("room");
    verify(fixture.session()).sendMessage(any());
    clearInvocations(fixture.session());
    fixture.socket().afterConnectionClosed(fixture.session(), CloseStatus.NORMAL);
    fixture.socket().broadcast("room");
    verify(fixture.session(), never()).sendMessage(any());
    verify(fixture.rooms(), times(2)).view("guest", "room");
  }
}
