package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;

import dev.mtgcmd.catalog.JsonHttp;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

class CardHttpConfigurationTest {
  @Test
  void contextOwnsConfiguredClientWhileAdapterBorrowsIt() throws Exception {
    // Client is owned and closed by the lexical Spring context below.
    @SuppressWarnings("PMD.CloseResource")
    final HttpClient client;
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(CardHttpConfiguration.class)) {
      client = context.getBean(HttpClient.class);
      assertThat(client.connectTimeout()).contains(Duration.ofSeconds(8));
      assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
      // Spring owns this borrowed adapter and its shared client.
      @SuppressWarnings("PMD.CloseResource")
      final JsonHttp adapter = context.getBean(JsonHttp.class);
      adapter.close();
      assertThat(client.isTerminated()).isFalse();
    }
    assertThat(client.awaitTermination(Duration.ofSeconds(2))).isTrue();
  }

  @TestConfiguration
  static class FailedStartup {
    @Bean
    Object fail(JsonHttp adapter) {
      throw new IllegalStateException("startup failed");
    }
  }

  @Test
  void failedContextStartupClosesItsCreatedClient() throws Exception {
    final AtomicReference<HttpClient> observed = new AtomicReference<>();
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context
          .getBeanFactory()
          .addBeanPostProcessor(
              new BeanPostProcessor() {
                // This callback observes a Spring-owned bean; it must not close it during
                // initialization.
                @SuppressWarnings("PMD.CloseResource")
                public Object postProcessAfterInitialization(final Object bean, String name) {
                  if (bean instanceof HttpClient client) {
                    observed.set(client);
                  }
                  return bean;
                }
              });
      context.register(CardHttpConfiguration.class, FailedStartup.class);
      assertThatThrownBy(context::refresh).hasRootCauseMessage("startup failed");
      assertThat(observed.get()).isNotNull();
      assertThat(observed.get().awaitTermination(Duration.ofSeconds(2))).isTrue();
    }
  }
}
