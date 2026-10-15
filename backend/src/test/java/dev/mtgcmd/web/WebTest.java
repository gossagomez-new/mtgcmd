package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.Application;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import java.util.Objects;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebTest {
  private static final String CONTENT_TYPE = "Content-Type";

  private static final String ORIGIN_HEADER = "Origin";

  private static final String CSRF_HEADER = "X-CSRF-Token";

  private static final String JSON_CONTENT_TYPE = "application/json";

  private static final String LOCAL_ORIGIN = "http://127.0.0.1:";

  private static final String PUBLIC_ORIGIN = "http://table.test";

  static final EmbeddedPostgres POSTGRES;

  static {
    try {
      POSTGRES = EmbeddedPostgres.builder().setPort(0).start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(final DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () -> "jdbc:postgresql://127.0.0.1:" + POSTGRES.getPort() + "/postgres");
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "");
    registry.add("app.origin", () -> PUBLIC_ORIGIN);
  }

  @LocalServerPort int port;

  @Test
  void guestBootstrapSetsHttpOnlyCookieAndRequiresCsrfAndOriginForWrites() throws Exception {
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    try (HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build()) {
      final URI session = URI.create(LOCAL_ORIGIN + port + "/api/session");
      final HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(session).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("set-cookie").orElse(""))
          .contains("HttpOnly", "SameSite=Strict");
      final String csrf = new ObjectMapper().readTree(response.body()).get("csrf").asText();
      final URI rooms = URI.create(LOCAL_ORIGIN + port + "/api/rooms");
      final HttpResponse<String> missing =
          client.send(
              HttpRequest.newBuilder(rooms)
                  .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
                  .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"A\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(missing.statusCode()).isEqualTo(403);
      final HttpResponse<String> wrong =
          client.send(
              HttpRequest.newBuilder(rooms)
                  .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
                  .header(ORIGIN_HEADER, "https://evil.test")
                  .header(CSRF_HEADER, csrf)
                  .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"A\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(wrong.statusCode()).isEqualTo(403);
      final HttpResponse<String> valid =
          client.send(
              HttpRequest.newBuilder(rooms)
                  .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
                  .header(ORIGIN_HEADER, PUBLIC_ORIGIN)
                  .header(CSRF_HEADER, csrf)
                  .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"A\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(valid.statusCode()).isEqualTo(200);
      assertThat(valid.body()).contains("\"you\":0");
    }
  }

  @Test
  void standardCreationUsesTwoSeatsAndTwentyLifeWhileOldRequestsRemainCommander() throws Exception {
    final ObjectMapper mapper = new ObjectMapper();
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    try (HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build()) {
      final URI base = URI.create(LOCAL_ORIGIN + port + "/api/");
      final HttpResponse<String> session =
          client.send(
              HttpRequest.newBuilder(base.resolve("session")).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      final String csrf = mapper.readTree(session.body()).path("csrf").asText();
      for (final String format : new String[] {"STANDARD", "COMMANDER", "INVALID"}) {
        final HttpResponse<String> response =
            client.send(
                HttpRequest.newBuilder(base.resolve("rooms"))
                    .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
                    .header(ORIGIN_HEADER, PUBLIC_ORIGIN)
                    .header(CSRF_HEADER, csrf)
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            "{\"name\":\"Format host\",\"format\":\"" + format + "\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        if ("INVALID".equals(Objects.requireNonNull(format))) {
          assertThat(response.statusCode()).isEqualTo(400);
        } else {
          assertThat(response.statusCode()).isEqualTo(200);
          final JsonNode room = mapper.readTree(response.body());
          assertThat(room.path("format").asText()).isEqualTo(format);
          assertThat(room.path("seatCapacity").asInt())
              .isEqualTo("STANDARD".equals(Objects.requireNonNull(format)) ? 2 : 4);
          assertThat(room.path("players").get(0).path("counters").path("life").asInt())
              .isEqualTo("STANDARD".equals(Objects.requireNonNull(format)) ? 20 : 40);
        }
      }
    }
  }

  @Test
  void malformedFormatValuesAreBadRequestsAndNullDefaultsToCommander() throws Exception {
    final ObjectMapper mapper = new ObjectMapper();
    try (HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build()) {
      final URI base = URI.create(LOCAL_ORIGIN + port + "/api/");
      final HttpResponse<String> session =
          client.send(
              HttpRequest.newBuilder(base.resolve("session")).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      final String csrf = mapper.readTree(session.body()).path("csrf").asText();
      for (final String raw :
          new String[] {"null", "{}", "[]", "true", "123", "\"\"", "\"standard\""}) {
        final HttpResponse<String> response =
            client.send(
                HttpRequest.newBuilder(base.resolve("rooms"))
                    .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
                    .header(ORIGIN_HEADER, PUBLIC_ORIGIN)
                    .header(CSRF_HEADER, csrf)
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            "{\"name\":\"Host\",\"format\":" + raw + "}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
            .isEqualTo("null".equals(Objects.requireNonNull(raw)) ? 200 : 400);
        if ("null".equals(Objects.requireNonNull(raw))) {
          assertThat(mapper.readTree(response.body()).path("format").asText())
              .isEqualTo("COMMANDER");
        }
      }
    }
  }
}
