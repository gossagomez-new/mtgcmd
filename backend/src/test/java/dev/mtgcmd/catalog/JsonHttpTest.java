package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;

class JsonHttpTest {
  private static final String PROVIDER_URL = "https://example.test";

  private JsonHttp withFuture(final CompletableFuture<HttpResponse<byte[]>> future) {
    // Mockito client has no transport resources to close.
    final HttpClient client = mock(HttpClient.class);
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
  void asynchronousTransportFailuresKeepTheirOriginalCause(
      final Throwable cause, final Class<?> category) {
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp http = withFuture(CompletableFuture.failedFuture(cause));
    final Throwable failure = catchThrowable(() -> http.get(URI.create(PROVIDER_URL)));
    assertThat(failure).isExactlyInstanceOf(category).hasCauseInstanceOf(ExecutionException.class);
    assertThat(failure.getCause().getCause()).isSameAs(cause);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void waitingFailureCancelsRequestAndRestoresInterruption(final boolean interrupted)
      throws Exception {
    final CompletableFuture<HttpResponse<byte[]>> future = mock(CompletableFuture.class);
    final Exception cause =
        interrupted ? new InterruptedException("cancelled") : new TimeoutException("deadline");
    when(future.get(anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(cause);
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp http = withFuture(future);
    try {
      final Throwable failure = catchThrowable(() -> http.get(URI.create(PROVIDER_URL)));
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
    final InvalidCardServiceResponseException serviceFailure =
        new InvalidCardServiceResponseException("too large", null);
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp http = withFuture(CompletableFuture.failedFuture(serviceFailure));
    assertThat(catchThrowable(() -> http.get(URI.create(PROVIDER_URL)))).isSameAs(serviceFailure);
    final NullPointerException bug = new NullPointerException("subscriber bug");
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp failing = withFuture(CompletableFuture.failedFuture(bug));
    assertThat(catchThrowable(() -> failing.get(URI.create(PROVIDER_URL)))).isSameAs(bug);
  }

  @Test
  void boundsWholeResponseAndRejectsRedirects() throws Exception {
    final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    // finally stops the server, interrupts the pool, and verifies bounded termination.
    @SuppressWarnings("PMD.CloseResource")
    final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    try {
      server.setExecutor(pool);
      server.createContext(
          "/large",
          exchange -> {
            final byte[] bytes = new byte[256];
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
      try (JsonHttp http = new JsonHttp(Duration.ofSeconds(1), 128, 0)) {
        final URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        final Throwable large = catchThrowable(() -> http.get(base.resolve("/large")));
        assertThat(large).hasMessageContaining("too large");
        assertThat(large.getClass().getSimpleName())
            .isEqualTo("InvalidCardServiceResponseException");
        final Throwable redirect = catchThrowable(() -> http.get(base.resolve("/redirect")));
        assertThat(redirect).hasMessageContaining("302");
        assertThat(redirect.getClass().getSimpleName())
            .isEqualTo("CardServiceUnavailableException");
      }
    } finally {
      server.stop(0);
      pool.shutdownNow();
      assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void timeoutCoversSlowBodyAfterHeadersHaveArrived() throws Exception {
    final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    // finally stops the server, interrupts the pool, and verifies bounded termination.
    @SuppressWarnings("PMD.CloseResource")
    final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    try {
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
      try (JsonHttp http = new JsonHttp(Duration.ofMillis(100), 128, 0)) {
        final Throwable failure =
            catchThrowable(
                () ->
                    http.get(
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow")));
        assertThat(failure).hasMessageContaining("timed out").hasCauseInstanceOf(Exception.class);
        assertThat(failure.getClass().getSimpleName()).isEqualTo("CardServiceTimeoutException");
      }
    } finally {
      server.stop(0);
      pool.shutdownNow();
      assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void interruptionRetainsItsCauseAndRestoresInterruptStatus() {
    try (JsonHttp http = new JsonHttp()) {
      Thread.currentThread().interrupt();
      try {
        final Throwable failure = catchThrowable(() -> http.get(URI.create(PROVIDER_URL)));
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

  private HttpClient returning(final CompletableFuture<HttpResponse<byte[]>> future) {
    // Mockito client has no transport resources to close.
    final HttpClient client = mock(HttpClient.class);
    when(client.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenReturn(future);
    return client;
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<byte[]> response(final int status, final String body) {
    final HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
    return response;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void requestConfigurationAndBodyRemainCompatible(final boolean post) throws Exception {
    final CompletableFuture<HttpResponse<byte[]>> future =
        spy(CompletableFuture.completedFuture(response(200, "é雪")));
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = returning(future);
    final Duration timeout = Duration.ofSeconds(2);
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp http = new JsonHttp(timeout, 128, 0, client);
    final URI uri = URI.create("https://example.test/cards");
    final String actual = post ? http.post(uri, "{\"name\":\"é\"}") : http.get(uri);
    assertThat(actual).isEqualTo("é雪");
    final ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(client)
        .sendAsync(
            requestCaptor.capture(),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    final HttpRequest request = requestCaptor.getValue();
    assertThat(request.uri()).isEqualTo(uri);
    assertThat(request.method()).isEqualTo(post ? "POST" : "GET");
    assertThat(request.timeout()).contains(timeout);
    assertThat(request.headers().firstValue("User-Agent")).contains("CommanderTable/0.1");
    assertThat(request.headers().firstValue("Accept")).contains("application/json");
    if (post) {
      assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
      assertThat(body(request.bodyPublisher().orElseThrow())).isEqualTo("{\"name\":\"é\"}");
    } else {
      assertThat(request.bodyPublisher()).isEmpty();
    }
    verify(future, never()).cancel(anyBoolean());
  }

  private String body(final HttpRequest.BodyPublisher publisher) throws Exception {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final CompletableFuture<String> completed = new CompletableFuture<>();
    publisher.subscribe(
        new Flow.Subscriber<>() {
          public void onSubscribe(final Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          public void onNext(final ByteBuffer buffer) {
            final byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            bytes.writeBytes(chunk);
          }

          public void onError(final Throwable error) {
            completed.completeExceptionally(error);
          }

          public void onComplete() {
            completed.complete(bytes.toString(StandardCharsets.UTF_8));
          }
        });
    return completed.get(2, TimeUnit.SECONDS);
  }

  @ParameterizedTest
  @ValueSource(ints = {201, 204, 302, 400, 503})
  void non200ResponsesRetainMessageAndDoNotCancel(final int status) {
    final CompletableFuture<HttpResponse<byte[]>> future =
        spy(CompletableFuture.completedFuture(response(status, "ignored")));
    assertThatThrownBy(() -> withFuture(future).get(URI.create(PROVIDER_URL)))
        .isExactlyInstanceOf(CardServiceUnavailableException.class)
        .hasMessage("Card service returned " + status)
        .hasNoCause();
    verify(future, never()).cancel(anyBoolean());
  }

  @Test
  void emptySuccessBodyIsAccepted() {
    assertThat(
            withFuture(CompletableFuture.completedFuture(response(200, "")))
                .get(URI.create(PROVIDER_URL)))
        .isEmpty();
  }

  @ParameterizedTest
  @MethodSource("dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void everyServiceSubtypePropagatesUnchangedWithoutCancellation(
      final CardServiceException failure) {
    final CompletableFuture<HttpResponse<byte[]>> future =
        spy(CompletableFuture.<HttpResponse<byte[]>>failedFuture(failure));
    assertThat(catchThrowable(() -> withFuture(future).get(URI.create(PROVIDER_URL))))
        .isSameAs(failure);
    verify(future, never()).cancel(anyBoolean());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void unexpectedCheckedOrNullCauseRetainsWrapper(final boolean nullCause) throws Exception {
    final ExecutionException wrapper =
        new ExecutionException(nullCause ? null : new Exception("unexpected"));
    final CompletableFuture<HttpResponse<byte[]>> future = mock(CompletableFuture.class);
    when(future.get(anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(wrapper);
    assertThatThrownBy(() -> withFuture(future).get(URI.create(PROVIDER_URL)))
        .isExactlyInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected card request failure")
        .hasCause(wrapper);
    verify(future, never()).cancel(anyBoolean());
  }

  @Test
  void interruptionDuringSpacingNeverStartsRequest() {
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = mock(HttpClient.class);
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp http = new JsonHttp(Duration.ofSeconds(1), 128, 10, client);
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> http.get(URI.create(PROVIDER_URL)))
          .isExactlyInstanceOf(CardServiceInterruptedException.class)
          .hasMessage("Card request interrupted")
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      verifyNoInteractions(client);
    } finally {
      Thread.interrupted();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 128, 129})
  void responseSubscriberEnforcesExactBoundary(final int size) throws Exception {
    final CompletableFuture<HttpResponse<byte[]>> future =
        CompletableFuture.completedFuture(response(200, ""));
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = returning(future);
    withFutureClient(client).get(URI.create(PROVIDER_URL));
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<BodyHandler<byte[]>> captor =
        ArgumentCaptor.forClass(
            (Class<HttpResponse.BodyHandler<byte[]>>) (Class<?>) HttpResponse.BodyHandler.class);
    verify(client).sendAsync(any(HttpRequest.class), captor.capture());
    final BodySubscriber<byte[]> subscriber =
        captor.getValue().apply(mock(HttpResponse.ResponseInfo.class));
    final Flow.Subscription subscription = mock(Flow.Subscription.class);
    subscriber.onSubscribe(subscription);
    if (size > 0) {
      subscriber.onNext(List.of(ByteBuffer.wrap(new byte[size])));
    }
    subscriber.onComplete();
    if (size > 128) {
      assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().get(2, TimeUnit.SECONDS))
          .hasCauseInstanceOf(InvalidCardServiceResponseException.class);
      verify(subscription).cancel();
    } else {
      assertThat(subscriber.getBody().toCompletableFuture().get(2, TimeUnit.SECONDS)).hasSize(size);
      verify(subscription, never()).cancel();
    }
  }

  private JsonHttp withFutureClient(final HttpClient client) {
    return new JsonHttp(Duration.ofSeconds(1), 128, 0, client);
  }

  @Test
  void concurrentCallsRemainSerializedUntilTheFirstResponseCompletes() throws Exception {
    final CompletableFuture<HttpResponse<byte[]>> firstResponse = new CompletableFuture<>();
    final CompletableFuture<HttpResponse<byte[]>> secondResponse =
        CompletableFuture.completedFuture(response(200, "second"));
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = mock(HttpClient.class);
    final CountDownLatch firstStarted = new CountDownLatch(1);
    when(client.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenAnswer(
            invocation -> {
              firstStarted.countDown();
              return firstResponse;
            })
        .thenReturn(secondResponse);
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp http = new JsonHttp(Duration.ofSeconds(5), 128, 0, client);
    final FutureTask<String> first =
        new FutureTask<>(() -> http.get(URI.create("https://example.test/first")));
    final FutureTask<String> second =
        new FutureTask<>(() -> http.get(URI.create("https://example.test/second")));
    final Thread firstThread = new Thread(first);
    final Thread secondThread = new Thread(second);
    try {
      firstThread.start();
      assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
      secondThread.start();
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (secondThread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertThat(secondThread.getState()).isEqualTo(Thread.State.BLOCKED);
      verify(client, times(1))
          .sendAsync(
              any(HttpRequest.class),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
      firstResponse.complete(response(200, "first"));
      assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("first");
      assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo("second");
    } finally {
      firstResponse.complete(response(200, "cleanup"));
      firstThread.interrupt();
      secondThread.interrupt();
      firstThread.join(2000);
      secondThread.join(2000);
    }
  }

  @Test
  void synchronousFailurePropagatesAndDoesNotStrandLaterCalls() {
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = mock(HttpClient.class);
    final IllegalStateException bug = new IllegalStateException("synchronous failure");
    when(client.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenThrow(bug)
        .thenReturn(CompletableFuture.completedFuture(response(200, "recovered")));
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp http = withFutureClient(client);
    assertThat(catchThrowable(() -> http.get(URI.create(PROVIDER_URL)))).isSameAs(bug);
    assertThat(http.get(URI.create(PROVIDER_URL))).isEqualTo("recovered");
  }

  @Test
  void externalMonitorDoesNotBlockRequestExecution() throws Exception {
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp http = withFuture(CompletableFuture.completedFuture(response(200, "accepted")));
    try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
      synchronized (http) {
        assertThat(pool.submit(() -> http.get(URI.create(PROVIDER_URL))).get(2, TimeUnit.SECONDS))
            .isEqualTo("accepted");
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"timeout", "interrupt", "async", "status", "synchronous"})
  @SuppressWarnings("unchecked")
  void failedRequestReleasesLockForAnotherThread(final String failureType) throws Exception {
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = mock(HttpClient.class);
    CompletableFuture<HttpResponse<byte[]>> failed = mock(CompletableFuture.class);
    switch (failureType) {
      case "timeout" ->
          when(failed.get(anyLong(), eq(TimeUnit.MILLISECONDS)))
              .thenThrow(new TimeoutException("deadline"));
      case "interrupt" ->
          when(failed.get(anyLong(), eq(TimeUnit.MILLISECONDS)))
              .thenThrow(new InterruptedException("interrupted"));
      case "async" -> failed = CompletableFuture.failedFuture(new IOException("offline"));
      case "status" -> failed = CompletableFuture.completedFuture(response(503, ""));
      case "synchronous" -> {}
      default -> throw new IllegalArgumentException(failureType);
    }
    final CompletableFuture<HttpResponse<byte[]>> next =
        CompletableFuture.completedFuture(response(200, "next"));
    final OngoingStubbing<CompletableFuture<HttpResponse<byte[]>>> stubbing =
        when(
            client.sendAsync(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()));
    if ("synchronous".equals(Objects.requireNonNull(failureType))) {
      stubbing.thenThrow(new IllegalStateException("failed")).thenReturn(next);
    } else {
      stubbing.thenReturn(failed).thenReturn(next);
    }
    // Adapter borrows a Mockito client; it owns no transport resources.
    @SuppressWarnings("PMD.CloseResource")
    final JsonHttp http = withFutureClient(client);
    try {
      assertThat(catchThrowable(() -> http.get(URI.create(PROVIDER_URL)))).isNotNull();
    } finally {
      Thread.interrupted();
    }
    try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
      assertThat(pool.submit(() -> http.get(URI.create(PROVIDER_URL))).get(2, TimeUnit.SECONDS))
          .isEqualTo("next");
    }
  }
}
