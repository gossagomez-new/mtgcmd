package dev.mtgcmd.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.Application;
import dev.mtgcmd.game.Game.Definition;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Browser-test fixture only: never packaged in the production jar. */
public final class TestServer {
  static EmbeddedPostgres postgres;
  static ConfigurableApplicationContext context;

  public static void main(String[] args) throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(0).start();
    start();
    JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
    ObjectMapper mapper = context.getBean(ObjectMapper.class);
    for (String name :
        List.of(
            "Demo Commander", "Secret A", "Secret B", "Secret C", "Secret D", "Demo Forbidden")) {
      Definition card =
          new Definition(
              name,
              name,
              "",
              "Browser-test fixture",
              name.equals("Demo Commander")
                  ? "Legendary Creature"
                  : name.equals("Demo Forbidden") ? "Sorcery" : "Basic Land",
              List.of(),
              true,
              List.of(),
              Map.of(
                  "commander",
                  "legal",
                  "standard",
                  name.equals("Demo Forbidden") ? "banned" : "legal"),
              System.currentTimeMillis());
      jdbc.update(
          "INSERT INTO catalog(name,definition) VALUES (?,CAST(? AS jsonb))",
          name.toLowerCase(Locale.ROOT),
          mapper.writeValueAsString(card));
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  context.close();
                  try {
                    postgres.close();
                  } catch (Exception ignored) {
                  }
                }));
    new java.util.concurrent.CountDownLatch(1).await();
  }

  static void start() {
    context =
        SpringApplication.run(
            Application.class,
            "--server.port=8081",
            "--spring.datasource.url=jdbc:postgresql://127.0.0.1:"
                + postgres.getPort()
                + "/postgres",
            "--spring.datasource.username=postgres",
            "--spring.datasource.password=",
            "--app.origin=http://127.0.0.1:5173",
            "--app.test-support=true");
  }

  @RestController
  @ConditionalOnProperty(name = "app.test-support", havingValue = "true")
  public static class Restart {
    @PostMapping("/__test/restart")
    public Map<String, String> restart() {
      new Thread(
              () -> {
                try {
                  Thread.sleep(300);
                  context.close();
                  start();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              })
          .start();
      return Map.of("status", "restarting");
    }
  }
}
