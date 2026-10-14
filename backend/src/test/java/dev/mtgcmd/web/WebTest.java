package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;

import dev.mtgcmd.Application;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebTest {
  static final EmbeddedPostgres PG;

  static {
    try {
      PG = EmbeddedPostgres.builder().setPort(0).start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + PG.getPort() + "/postgres");
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "");
    r.add("app.origin", () -> "http://table.test");
  }

  @LocalServerPort int port;

  @Test
  void guestBootstrapSetsHttpOnlyCookieAndRequiresCsrfAndOriginForWrites() throws Exception {
    var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    var client = HttpClient.newBuilder().cookieHandler(cookies).build();
    URI session = URI.create("http://127.0.0.1:" + port + "/api/session");
    var response =
        client.send(
            HttpRequest.newBuilder(session).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("set-cookie").orElse(""))
        .contains("HttpOnly", "SameSite=Strict");
    String csrf =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response.body())
            .get("csrf")
            .asText();
    URI rooms = URI.create("http://127.0.0.1:" + port + "/api/rooms");
    var missing =
        client.send(
            HttpRequest.newBuilder(rooms)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"A\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(missing.statusCode()).isEqualTo(403);
    var wrong =
        client.send(
            HttpRequest.newBuilder(rooms)
                .header("Content-Type", "application/json")
                .header("Origin", "https://evil.test")
                .header("X-CSRF-Token", csrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"A\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(wrong.statusCode()).isEqualTo(403);
    var valid =
        client.send(
            HttpRequest.newBuilder(rooms)
                .header("Content-Type", "application/json")
                .header("Origin", "http://table.test")
                .header("X-CSRF-Token", csrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"A\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(valid.statusCode()).isEqualTo(200);
    assertThat(valid.body()).contains("\"you\":0");
  }

  @Test
  void standardCreationUsesTwoSeatsAndTwentyLifeWhileOldRequestsRemainCommander() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    var client = HttpClient.newBuilder().cookieHandler(cookies).build();
    URI base = URI.create("http://127.0.0.1:" + port + "/api/");
    var session =
        client.send(
            HttpRequest.newBuilder(base.resolve("session")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    String csrf = mapper.readTree(session.body()).path("csrf").asText();
    for (String format : new String[] {"STANDARD", "COMMANDER", "INVALID"}) {
      var response =
          client.send(
              HttpRequest.newBuilder(base.resolve("rooms"))
                  .header("Content-Type", "application/json")
                  .header("Origin", "http://table.test")
                  .header("X-CSRF-Token", csrf)
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"name\":\"Format host\",\"format\":\"" + format + "\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (format.equals("INVALID")) {
        assertThat(response.statusCode()).isEqualTo(400);
      } else {
        assertThat(response.statusCode()).isEqualTo(200);
        var room = mapper.readTree(response.body());
        assertThat(room.path("format").asText()).isEqualTo(format);
        assertThat(room.path("seatCapacity").asInt()).isEqualTo(format.equals("STANDARD") ? 2 : 4);
        assertThat(room.path("players").get(0).path("counters").path("life").asInt())
            .isEqualTo(format.equals("STANDARD") ? 20 : 40);
      }
    }
  }

  @Test
  void malformedFormatValuesAreBadRequestsAndNullDefaultsToCommander() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    URI base = URI.create("http://127.0.0.1:" + port + "/api/");
    var session =
        client.send(
            HttpRequest.newBuilder(base.resolve("session")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    String csrf = mapper.readTree(session.body()).path("csrf").asText();
    for (String raw : new String[] {"null", "{}", "[]", "true", "123", "\"\"", "\"standard\""}) {
      var response =
          client.send(
              HttpRequest.newBuilder(base.resolve("rooms"))
                  .header("Content-Type", "application/json")
                  .header("Origin", "http://table.test")
                  .header("X-CSRF-Token", csrf)
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"name\":\"Host\",\"format\":" + raw + "}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(raw.equals("null") ? 200 : 400);
      if (raw.equals("null"))
        assertThat(mapper.readTree(response.body()).path("format").asText()).isEqualTo("COMMANDER");
    }
  }
}
