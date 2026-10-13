package dev.mtgcmd.testsupport;

import com.fasterxml.jackson.databind.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Explicit artifact smoke check; live catalog requests run only with --live-import. */
public final class JarSmoke {
  public static void main(String[] args) throws Exception {
    Path jar = Path.of(args[0]).toAbsolutePath();
    try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().setPort(0).start()) {
      int port;
      try (var available = new java.net.ServerSocket(0)) {
        port = available.getLocalPort();
      }
      String origin = "http://127.0.0.1:" + port;
      Process server =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "-jar",
                  jar.toString(),
                  "--server.port=" + port,
                  "--spring.datasource.url=jdbc:postgresql://127.0.0.1:"
                      + postgres.getPort()
                      + "/postgres",
                  "--spring.datasource.username=postgres",
                  "--spring.datasource.password=",
                  "--app.origin=" + origin)
              .redirectErrorStream(true)
              .redirectOutput(Path.of("/tmp/mtgcmd-jar-smoke.log").toFile())
              .start();
      try {
        var client =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
        HttpResponse<String> index = null;
        for (int attempt = 0; attempt < 100; attempt++) {
          try {
            index = get(client, origin + "/");
            if (index.statusCode() == 200) break;
          } catch (Exception ignored) {
          }
          if (!server.isAlive())
            throw new IllegalStateException(
                "Packaged server exited; see /tmp/mtgcmd-jar-smoke.log");
          Thread.sleep(200);
        }
        require(
            index != null && index.statusCode() == 200 && index.body().contains("id=\"root\""),
            "Packaged React index missing");
        var script = java.util.regex.Pattern.compile("src=\"([^\"]+\\.js)\"").matcher(index.body());
        require(script.find(), "Bundled JavaScript missing");
        require(
            get(client, origin + script.group(1)).statusCode() == 200,
            "Bundled JavaScript route missing");
        require(
            get(client, origin + "/__test/restart").statusCode() == 404,
            "Test-only restart endpoint leaked into production");
        var mapper = new ObjectMapper();
        String csrf =
            mapper.readTree(get(client, origin + "/api/session").body()).path("csrf").asText();
        var room =
            mapper.readTree(
                post(
                        client,
                        origin,
                        csrf,
                        "/api/rooms",
                        mapper.writeValueAsString(Map.of("name", "Artifact check")))
                    .body());
        String roomId = room.path("id").asText();
        require(!roomId.isBlank(), "Room creation failed");
        require(
            get(client, origin + "/room/" + roomId).statusCode() == 200, "SPA room route missing");
        if (args.length > 1 && args[1].equals("--live-import")) {
          var preview =
              mapper.readTree(
                  post(
                          client,
                          origin,
                          csrf,
                          "/api/rooms/" + roomId + "/decks/preview",
                          mapper.writeValueAsString(
                              Map.of(
                                  "text",
                                  "Commander\n1 Gisa and Geralf\nDeck\n1 Sol Ring\n98 Island")))
                      .body());
          require(preview.path("count").asInt() == 100, "Live catalog import failed");
          var loaded =
              mapper.readTree(
                  post(
                          client,
                          origin,
                          csrf,
                          "/api/rooms/" + roomId + "/deck",
                          mapper.writeValueAsString(
                              Map.of(
                                  "draftId",
                                  preview.path("draftId").asText(),
                                  "commanders",
                                  mapper.convertValue(preview.path("suggested"), List.class),
                                  "commandId",
                                  UUID.randomUUID().toString(),
                                  "revision",
                                  room.path("revision").asLong())))
                      .body());
          require(
              loaded.path("players").get(0).path("deckCount").asInt() == 100,
              "Live deck load failed");
          System.out.println("LIVE IMPORT: 100-card Scryfall-backed deck preview and load passed");
        }
        System.out.println(
            "JAR SMOKE: React index/assets, room route, guest session, API, and production-only packaging passed");
      } finally {
        server.destroy();
        if (!server.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) server.destroyForcibly();
      }
    }
  }

  private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> post(
      HttpClient client, String origin, String csrf, String path, String body) throws Exception {
    var response =
        client.send(
            HttpRequest.newBuilder(URI.create(origin + path))
                .timeout(Duration.ofSeconds(30))
                .header("Origin", origin)
                .header("X-CSRF-Token", csrf)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    require(response.statusCode() == 200, "Request " + path + " returned " + response.statusCode());
    return response;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
