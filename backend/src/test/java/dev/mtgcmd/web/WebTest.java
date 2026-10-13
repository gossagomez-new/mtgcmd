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
}
