package dev.mtgcmd.catalog;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Fixed destinations are chosen by adapters; no redirects or unbounded responses. */
public final class JsonHttp {
  private final Duration timeout;
  private final int maxBytes;
  private final int spacingMillis;
  private final HttpClient client;

  public JsonHttp() {
    this(Duration.ofSeconds(15), 5_000_000, 110);
  }

  public JsonHttp(Duration timeout, int maxBytes, int spacingMillis) {
    this(
        timeout,
        maxBytes,
        spacingMillis,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  JsonHttp(Duration timeout, int maxBytes, int spacingMillis, HttpClient client) {
    if (timeout.isNegative() || timeout.isZero() || maxBytes < 1 || spacingMillis < 0)
      throw new IllegalArgumentException("Invalid HTTP limits");
    this.timeout = timeout;
    this.maxBytes = maxBytes;
    this.spacingMillis = spacingMillis;
    this.client = client;
  }

  public String get(URI uri) {
    return send(HttpRequest.newBuilder(uri).GET());
  }

  public String post(URI uri, String body) {
    return send(
        HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)));
  }

  private synchronized String send(HttpRequest.Builder builder) {
    CompletableFuture<HttpResponse<byte[]>> future = null;
    try {
      Thread.sleep(spacingMillis);
      future =
          client.sendAsync(
              builder
                  .timeout(timeout)
                  .header("User-Agent", "CommanderTable/0.1")
                  .header("Accept", "application/json")
                  .build(),
              info -> new BoundedBody(maxBytes));
      var response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (response.statusCode() != 200)
        throw new CardServiceUnavailableException(
            "Card service returned " + response.statusCode(), null);
      return new String(response.body(), StandardCharsets.UTF_8);
    } catch (InterruptedException e) {
      if (future != null) future.cancel(true);
      Thread.currentThread().interrupt();
      throw new CardServiceInterruptedException("Card request interrupted", e);
    } catch (TimeoutException e) {
      if (future != null) future.cancel(true);
      throw new CardServiceTimeoutException("Card request timed out; try again later", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof CardServiceException cause) throw cause;
      if (e.getCause() instanceof HttpTimeoutException)
        throw new CardServiceTimeoutException("Card request timed out; try again later", e);
      if (e.getCause() instanceof IOException)
        throw new CardServiceUnavailableException(
            "Card service unavailable; retry or use a cached deck", e);
      if (e.getCause() instanceof RuntimeException cause) throw cause;
      throw new IllegalStateException("Unexpected card request failure", e);
    }
  }

  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate =
        HttpResponse.BodySubscribers.ofByteArray();
    private final int limit;
    private Flow.Subscription subscription;
    private long received;
    private boolean done;

    BoundedBody(int limit) {
      this.limit = limit;
    }

    public CompletionStage<byte[]> getBody() {
      return delegate.getBody();
    }

    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      delegate.onSubscribe(subscription);
    }

    public void onNext(List<ByteBuffer> buffers) {
      if (done) return;
      for (ByteBuffer buffer : buffers) received += buffer.remaining();
      if (received > limit) {
        done = true;
        subscription.cancel();
        delegate.onError(
            new InvalidCardServiceResponseException("Card service response too large", null));
      } else delegate.onNext(buffers);
    }

    public void onError(Throwable error) {
      if (!done) {
        done = true;
        delegate.onError(error);
      }
    }

    public void onComplete() {
      if (!done) {
        done = true;
        delegate.onComplete();
      }
    }
  }
}
