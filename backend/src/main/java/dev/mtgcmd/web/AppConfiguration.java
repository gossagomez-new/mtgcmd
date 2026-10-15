package dev.mtgcmd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.GameEngine;
import dev.mtgcmd.store.RoomStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.server.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
public class AppConfiguration {
  @Bean
  GameEngine engine() {
    return new GameEngine();
  }

  @Bean
  RoomStore rooms(
      final JdbcTemplate jdbc,
      final PlatformTransactionManager manager,
      final ObjectMapper mapper,
      final GameEngine engine) {
    return new RoomStore(jdbc, new TransactionTemplate(manager), mapper, engine);
  }

  @Bean
  GuestSessions guests(
      final JdbcTemplate jdbc, @Value("${app.secure-cookie}") final boolean secure) {
    return new GuestSessions(jdbc, secure);
  }

  @Bean
  SecurityFilter security(final GuestSessions guests, @Value("${app.origin}") final String origin) {
    return new SecurityFilter(guests, origin);
  }

  @Bean
  GameSocket socket(final ObjectMapper mapper, final RoomStore rooms) {
    return new GameSocket(mapper, rooms);
  }

  @Configuration
  @EnableWebSocket
  static class Sockets implements WebSocketConfigurer {
    private final GameSocket handler;
    private final GuestSessions guests;
    private final RoomStore rooms;
    private final String origin;

    Sockets(
        final GameSocket handler,
        final GuestSessions guests,
        final RoomStore rooms,
        @Value("${app.origin}") final String origin) {
      this.handler = handler;
      this.guests = guests;
      this.rooms = rooms;
      this.origin = origin;
    }

    public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
      registry
          .addHandler(handler, "/ws")
          .setAllowedOrigins(origin)
          .addInterceptors(
              new HandshakeInterceptor() {
                public boolean beforeHandshake(
                    final ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler handler,
                    final Map<String, Object> attributes) {
                  try {
                    final HttpServletRequest servlet =
                        ((ServletServerHttpRequest) request).getServletRequest();
                    final String guest = guests.require(servlet).hash();
                    final String room =
                        UriComponentsBuilder.fromUri(request.getURI())
                            .build()
                            .getQueryParams()
                            .getFirst("room");
                    if (room == null) {
                      return false;
                    }
                    attributes.put("seat", rooms.seat(guest, room));
                    attributes.put("room", room);
                    attributes.put("guest", guest);
                    return true;
                  } catch (Exception e) {
                    return false;
                  }
                }

                public void afterHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler handler,
                    Exception error) {}
              });
    }
  }
}
