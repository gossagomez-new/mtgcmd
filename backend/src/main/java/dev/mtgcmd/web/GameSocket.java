package dev.mtgcmd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.Game;
import dev.mtgcmd.store.RoomStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

public final class GameSocket extends TextWebSocketHandler {
  private final ObjectMapper mapper;
  private final RoomStore rooms;
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  public GameSocket(ObjectMapper mapper, RoomStore rooms) {
    this.mapper = mapper;
    this.rooms = rooms;
  }

  public void afterConnectionEstablished(WebSocketSession raw) {
    raw.setTextMessageSizeLimit(12000);
    WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 10000, 500000);
    sessions.put(raw.getId(), session);
    sendView(session);
    presence(room(session));
  }

  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    WebSocketSession target = sessions.get(session.getId());
    if (target == null) return;
    try {
      Game.Command command = mapper.readValue(message.getPayload(), Game.Command.class);
      var view = rooms.execute(guest(session), room(session), command);
      send(target, Map.of("type", "ack", "commandId", command.id(), "view", view));
      broadcast(room(session));
    } catch (IllegalArgumentException e) {
      Map<String, Object> error = new LinkedHashMap<>();
      error.put("type", "error");
      error.put("message", e.getMessage());
      try {
        error.put("view", rooms.view(guest(session), room(session)));
      } catch (Exception ignored) {
      }
      send(target, error);
    } catch (Exception e) {
      send(target, Map.of("type", "error", "message", "Action failed. Refresh before retrying."));
    }
  }

  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
    presence(room(session));
  }

  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    session.close(CloseStatus.SERVER_ERROR);
  }

  public void broadcast(String room) {
    for (WebSocketSession s : sessions.values()) if (room.equals(room(s))) sendView(s);
  }

  private void sendView(WebSocketSession s) {
    try {
      send(s, Map.of("type", "snapshot", "view", rooms.view(guest(s), room(s))));
    } catch (Exception e) {
      send(s, Map.of("type", "error", "message", "Game temporarily unavailable"));
    }
  }

  private void presence(String room) {
    List<Integer> online =
        sessions.values().stream()
            .filter(s -> room.equals(room(s)))
            .map(s -> (Integer) s.getAttributes().get("seat"))
            .distinct()
            .sorted()
            .toList();
    sessions.values().stream()
        .filter(s -> room.equals(room(s)))
        .forEach(s -> send(s, Map.of("type", "presence", "online", online)));
  }

  private void send(WebSocketSession s, Object value) {
    try {
      if (s.isOpen()) s.sendMessage(new TextMessage(mapper.writeValueAsString(value)));
    } catch (Exception e) {
      try {
        s.close(CloseStatus.SERVER_ERROR);
      } catch (Exception ignored) {
      }
    }
  }

  private static String guest(WebSocketSession s) {
    return (String) s.getAttributes().get("guest");
  }

  private static String room(WebSocketSession s) {
    return (String) s.getAttributes().get("room");
  }
}
