package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

class JsonHttpOwnershipTest {
  private static final String PROVIDER_URL = "https://example.test";

  private JsonHttp owned(final HttpClient client) {
    try (MockedStatic<HttpClient> construction = mockStatic(HttpClient.class)) {
      final Builder builder = mock(HttpClient.Builder.class, RETURNS_SELF);
      when(builder.build()).thenReturn(client);
      construction.when(HttpClient::newBuilder).thenReturn(builder);
      return new JsonHttp();
    }
  }

  @Test
  void ownedCloseIsIdempotentAndRejectsFurtherRequests() {
    // Mockito client has no transport resources to close.
    final HttpClient client = mock(HttpClient.class);
    try (JsonHttp http = owned(client)) {
      http.close();
      http.close();
      verify(client, times(1)).close();
      assertThatThrownBy(() -> http.get(URI.create(PROVIDER_URL)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("HTTP adapter is closed");
      verify(client, never()).sendAsync(any(), any());
    }
  }

  @Test
  void closeFailurePropagatesWithoutClosingTwice() {
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = mock(HttpClient.class);
    final IllegalStateException failure = new IllegalStateException("close failed");
    doThrow(failure).when(client).close();
    try (JsonHttp http = owned(client)) {
      assertThatThrownBy(http::close).isSameAs(failure);
      http.close();
      verify(client, times(1)).close();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void borrowedCloseDoesNotCloseOrDisableTheClient() {
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = mock(HttpClient.class);
    final HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(new byte[0]);
    when(client.sendAsync(
            any(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenReturn(CompletableFuture.completedFuture(response));
    try (JsonHttp http = new JsonHttp(client)) {
      http.get(URI.create(PROVIDER_URL));
      http.close();
      http.close();
      http.get(URI.create(PROVIDER_URL));
      verify(client, never()).close();
      verify(client, times(2)).sendAsync(any(), any());
    }
  }

  @ParameterizedTest
  @CsvSource({"0,128,0", "1000,0,0", "1000,128,-1"})
  void invalidOwnedLimitsDoNotAllocateClient(
      final long millis, final int maxBytes, final int spacing) {
    try (MockedStatic<HttpClient> construction = mockStatic(HttpClient.class)) {
      assertThatThrownBy(() -> new JsonHttp(Duration.ofMillis(millis), maxBytes, spacing))
          .hasMessage("Invalid HTTP limits");
      construction.verifyNoInteractions();
    }
  }

  @Test
  void invalidBorrowedLimitsLeaveCallerResourceUntouched() {
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = mock(HttpClient.class);
    assertThatThrownBy(() -> new JsonHttp(Duration.ZERO, 128, 0, client))
        .hasMessage("Invalid HTTP limits");
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  void ownedCloseWaitsForAdmittedRequestBeforeClosingClient() throws Exception {
    // Mockito client has no transport resources to close.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client = mock(HttpClient.class);
    final CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
    final CountDownLatch submitted = new CountDownLatch(1);
    when(client.sendAsync(
            any(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenAnswer(
            invocation -> {
              submitted.countDown();
              return pending;
            });
    try (JsonHttp http = owned(client)) {
      final FutureTask<String> request = new FutureTask<>(() -> http.get(URI.create(PROVIDER_URL)));
      final FutureTask<Void> closing =
          new FutureTask<>(
              () -> {
                http.close();
                return null;
              });
      final Thread requester = new Thread(request);
      final Thread closer = new Thread(closing);
      try {
        requester.start();
        assertThat(submitted.await(2, TimeUnit.SECONDS)).isTrue();
        closer.start();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (closer.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
          Thread.onSpinWait();
        }
        assertThat(closer.getState()).isEqualTo(Thread.State.BLOCKED);
        verify(client, never()).close();
        final HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new byte[0]);
        pending.complete(response);
        assertThat(request.get(2, TimeUnit.SECONDS)).isEmpty();
        closing.get(2, TimeUnit.SECONDS);
        verify(client).close();
      } finally {
        pending.cancel(true);
        requester.interrupt();
        closer.interrupt();
        requester.join(2000);
        closer.join(2000);
      }
    }
  }
}
