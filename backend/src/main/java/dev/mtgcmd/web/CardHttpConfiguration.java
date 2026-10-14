package dev.mtgcmd.web;

import dev.mtgcmd.catalog.JsonHttp;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring owns the shared client; adapters borrow its application-scoped lifetime. */
@Configuration
public class CardHttpConfiguration {
  @Bean(destroyMethod = "close")
  HttpClient cardHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Bean
  JsonHttp jsonHttp(HttpClient cardHttpClient) {
    return new JsonHttp(cardHttpClient);
  }
}
