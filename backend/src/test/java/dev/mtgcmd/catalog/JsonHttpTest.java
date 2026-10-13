package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class JsonHttpTest {
  @Test
  void boundsWholeResponseAndRejectsRedirects() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var pool = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(pool);
    server.createContext(
        "/large",
        exchange -> {
          byte[] bytes = new byte[256];
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.createContext(
        "/redirect",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/large");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.start();
    try {
      JsonHttp http = new JsonHttp(Duration.ofSeconds(1), 128, 0);
      URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
      assertThatThrownBy(() -> http.get(base.resolve("/large"))).hasMessageContaining("too large");
      assertThatThrownBy(() -> http.get(base.resolve("/redirect"))).hasMessageContaining("302");
    } finally {
      server.stop(0);
      pool.shutdownNow();
    }
  }

  @Test
  void timeoutCoversSlowBodyAfterHeadersHaveArrived() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var pool = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(pool);
    server.createContext(
        "/slow",
        exchange -> {
          exchange.sendResponseHeaders(200, 0);
          exchange.getResponseBody().flush();
          try {
            Thread.sleep(700);
            exchange.getResponseBody().write("{}".getBytes());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      JsonHttp http = new JsonHttp(Duration.ofMillis(100), 128, 0);
      assertThatThrownBy(
              () ->
                  http.get(
                      URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow")))
          .hasMessageContaining("timed out");
    } finally {
      server.stop(0);
      pool.shutdownNow();
    }
  }
}
