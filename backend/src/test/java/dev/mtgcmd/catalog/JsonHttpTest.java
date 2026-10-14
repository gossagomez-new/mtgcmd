package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class JsonHttpTest {
  private JsonHttp withFuture(CompletableFuture<HttpResponse<byte[]>> future) {
    var client = mock(HttpClient.class);
    when(client.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenReturn(future);
    return new JsonHttp(Duration.ofSeconds(1), 128, 0, client);
  }

  static Stream<Arguments> asynchronousFailures() {
    return Stream.of(
        Arguments.of(new HttpTimeoutException("HTTP deadline"), CardServiceTimeoutException.class),
        Arguments.of(
            new ConnectException("connection refused"), CardServiceUnavailableException.class),
        Arguments.of(new IOException("connection closed"), CardServiceUnavailableException.class));
  }

  @ParameterizedTest
  @MethodSource("asynchronousFailures")
  void asynchronousTransportFailuresKeepTheirOriginalCause(Throwable cause, Class<?> category) {
    var http = withFuture(CompletableFuture.failedFuture(cause));
    Throwable failure = catchThrowable(() -> http.get(URI.create("https://example.test")));
    assertThat(failure).isExactlyInstanceOf(category).hasCauseInstanceOf(ExecutionException.class);
    assertThat(failure.getCause().getCause()).isSameAs(cause);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void waitingFailureCancelsRequestAndRestoresInterruption(boolean interrupted) throws Exception {
    CompletableFuture<HttpResponse<byte[]>> future = mock(CompletableFuture.class);
    Exception cause =
        interrupted ? new InterruptedException("cancelled") : new TimeoutException("deadline");
    when(future.get(anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(cause);
    var http = withFuture(future);
    try {
      Throwable failure = catchThrowable(() -> http.get(URI.create("https://example.test")));
      assertThat(failure)
          .isExactlyInstanceOf(
              interrupted
                  ? CardServiceInterruptedException.class
                  : CardServiceTimeoutException.class)
          .hasCause(cause);
      verify(future).cancel(true);
      assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupted);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void asynchronousTypedFailureIsNotWrappedAndProgrammingFailureIsNotMisclassified() {
    var serviceFailure = new InvalidCardServiceResponseException("too large", null);
    var http = withFuture(CompletableFuture.failedFuture(serviceFailure));
    assertThat(catchThrowable(() -> http.get(URI.create("https://example.test"))))
        .isSameAs(serviceFailure);
    var bug = new NullPointerException("subscriber bug");
    JsonHttp failing = withFuture(CompletableFuture.failedFuture(bug));
    assertThat(catchThrowable(() -> failing.get(URI.create("https://example.test")))).isSameAs(bug);
  }

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
      Throwable large = catchThrowable(() -> http.get(base.resolve("/large")));
      assertThat(large).hasMessageContaining("too large");
      assertThat(large.getClass().getSimpleName()).isEqualTo("InvalidCardServiceResponseException");
      Throwable redirect = catchThrowable(() -> http.get(base.resolve("/redirect")));
      assertThat(redirect).hasMessageContaining("302");
      assertThat(redirect.getClass().getSimpleName()).isEqualTo("CardServiceUnavailableException");
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
      Throwable failure =
          catchThrowable(
              () ->
                  http.get(
                      URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow")));
      assertThat(failure).hasMessageContaining("timed out").hasCauseInstanceOf(Exception.class);
      assertThat(failure.getClass().getSimpleName()).isEqualTo("CardServiceTimeoutException");
    } finally {
      server.stop(0);
      pool.shutdownNow();
    }
  }

  @Test
  void interruptionRetainsItsCauseAndRestoresInterruptStatus() {
    JsonHttp http = new JsonHttp();
    Thread.currentThread().interrupt();
    try {
      Throwable failure = catchThrowable(() -> http.get(URI.create("https://example.test")));
      assertThat(failure)
          .hasMessage("Card request interrupted")
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(failure.getClass().getSimpleName()).isEqualTo("CardServiceInterruptedException");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
