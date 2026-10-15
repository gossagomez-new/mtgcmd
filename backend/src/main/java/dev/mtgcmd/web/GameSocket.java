package dev.mtgcmd.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.catalog.CardServiceException;
import dev.mtgcmd.game.Game;
import dev.mtgcmd.store.RoomStore;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

public final class GameSocket extends TextWebSocketHandler {
  private static final String TYPE_FIELD = "type";

  private static final Logger LOG = LoggerFactory.getLogger(GameSocket.class);
  private final ObjectMapper mapper;
  private final RoomStore rooms;
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  public GameSocket(final ObjectMapper mapper, final RoomStore rooms) {
    this.mapper = mapper;
    this.rooms = rooms;
  }

  public void afterConnectionEstablished(final WebSocketSession raw) {
    raw.setTextMessageSizeLimit(12000);
    final WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 10000, 500000);
    sessions.put(raw.getId(), session);
    sendView(session);
    presence(room(session));
  }

  protected void handleTextMessage(final WebSocketSession session, final TextMessage message) {
    // Borrowed registry connection: close callbacks and transport failures own termination.
    @SuppressWarnings("PMD.CloseResource")
    final WebSocketSession target = sessions.get(session.getId());
    if (target == null) {
      return;
    }
    try {
      final Game.Command command = mapper.readValue(message.getPayload(), Game.Command.class);
      final Map<String, Object> view = rooms.execute(guest(session), room(session), command);
      send(target, Map.of(TYPE_FIELD, "ack", "commandId", command.id(), "view", view));
      broadcast(room(session));
    } catch (IllegalArgumentException e) {
      if (e instanceof CardServiceException) {
        LOG.warn("Card service operation failed: room={}", room(session), e);
      }
      final Map<String, Object> error = new LinkedHashMap<>();
      error.put(TYPE_FIELD, "error");
      error.put("message", e.getMessage());
      try {
        error.put("view", rooms.view(guest(session), room(session)));
      } catch (Exception recoveryFailure) {
        LOG.error("WebSocket view recovery failed: room={}", room(session), recoveryFailure);
      }
      send(target, error);
    } catch (Exception e) {
      LOG.error("WebSocket command failed: room={}", room(session), e);
      send(
          target,
          Map.of(TYPE_FIELD, "error", "message", "Action failed. Refresh before retrying."));
    }
  }

  public void afterConnectionClosed(final WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
    presence(room(session));
  }

  public void handleTransportError(final WebSocketSession session, final Throwable exception)
      throws Exception {
    try {
      session.close(CloseStatus.SERVER_ERROR);
    } catch (Exception closeFailure) {
      if (closeFailure != exception) {
        exception.addSuppressed(closeFailure);
      }
      throw closeFailure;
    } finally {
      LOG.warn(
          "WebSocket transport failed: room={}, connection={}",
          room(session),
          session.getId(),
          exception);
    }
  }

  // Registry entries borrow live framework connections; broadcasting must not terminate them.
  @SuppressWarnings("PMD.CloseResource")
  public void broadcast(final String room) {
    for (final WebSocketSession s : sessions.values()) {
      if (room.equals(room(s))) {
        sendView(s);
      }
    }
  }

  private void sendView(final WebSocketSession session) {
    try {
      send(
          session,
          Map.of(TYPE_FIELD, "snapshot", "view", rooms.view(guest(session), room(session))));
    } catch (Exception e) {
      if (e instanceof CardServiceException) {
        LOG.warn("Card service view failed: room={}", room(session), e);
      } else {
        LOG.error("WebSocket snapshot failed: room={}", room(session), e);
      }
      send(session, Map.of(TYPE_FIELD, "error", "message", "Game temporarily unavailable"));
    }
  }

  private void presence(final String room) {
    final List<Integer> online =
        sessions.values().stream()
            .filter(s -> room.equals(room(s)))
            .map(s -> (Integer) s.getAttributes().get("seat"))
            .distinct()
            .sorted()
            .toList();
    sessions.values().stream()
        .filter(s -> room.equals(room(s)))
        .forEach(s -> send(s, Map.of(TYPE_FIELD, "presence", "online", online)));
  }

  private void send(final WebSocketSession session, final Object value) {
    try {
      if (session.isOpen()) {
        session.sendMessage(new TextMessage(mapper.writeValueAsString(value)));
      }
    } catch (Exception e) {
      try {
        session.close(CloseStatus.SERVER_ERROR);
      } catch (Exception closeFailure) {
        if (closeFailure != e) {
          e.addSuppressed(closeFailure);
        }
      }
      if (e instanceof IOException && !(e instanceof JsonProcessingException)) {
        LOG.warn(
            "WebSocket send failed: room={}, connection={}", room(session), session.getId(), e);
      } else {
        LOG.error(
            "WebSocket send failed: room={}, connection={}", room(session), session.getId(), e);
      }
    }
  }

  private static String guest(final WebSocketSession session) {
    return (String) session.getAttributes().get("guest");
  }

  private static String room(final WebSocketSession session) {
    return (String) session.getAttributes().get("room");
  }
}
