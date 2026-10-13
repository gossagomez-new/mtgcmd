package dev.mtgcmd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.catalog.*;
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
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      ObjectMapper mapper,
      GameEngine engine) {
    return new RoomStore(jdbc, new TransactionTemplate(manager), mapper, engine);
  }

  @Bean
  GuestSessions guests(JdbcTemplate jdbc, @Value("${app.secure-cookie}") boolean secure) {
    return new GuestSessions(jdbc, secure);
  }

  @Bean
  SecurityFilter security(GuestSessions guests, @Value("${app.origin}") String origin) {
    return new SecurityFilter(guests, origin);
  }

  @Bean
  JsonHttp jsonHttp() {
    return new JsonHttp();
  }

  @Bean
  DeckImporter importer(JdbcTemplate jdbc, ObjectMapper mapper, JsonHttp http) {
    return new DeckImporter(new CardCatalog(jdbc, mapper, http), http::get);
  }

  @Bean
  GameSocket socket(ObjectMapper mapper, RoomStore rooms) {
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
        GameSocket handler,
        GuestSessions guests,
        RoomStore rooms,
        @Value("${app.origin}") String origin) {
      this.handler = handler;
      this.guests = guests;
      this.rooms = rooms;
      this.origin = origin;
    }

    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
      registry
          .addHandler(handler, "/ws")
          .setAllowedOrigins(origin)
          .addInterceptors(
              new HandshakeInterceptor() {
                public boolean beforeHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler handler,
                    Map<String, Object> attributes) {
                  try {
                    HttpServletRequest servlet =
                        ((ServletServerHttpRequest) request).getServletRequest();
                    String guest = guests.require(servlet).hash();
                    String room =
                        UriComponentsBuilder.fromUri(request.getURI())
                            .build()
                            .getQueryParams()
                            .getFirst("room");
                    if (room == null) return false;
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
