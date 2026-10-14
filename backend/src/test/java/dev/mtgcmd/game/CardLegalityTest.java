package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CardLegalityTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);

  private Game.Definition definition(String status, Long observed) {
    return new Game.Definition(
        "id",
        "Card",
        "",
        "",
        "Creature",
        List.of(),
        true,
        List.of(),
        status == null ? Map.of() : Map.of("standard", status),
        observed);
  }

  @ParameterizedTest
  @ValueSource(strings = {"legal", "banned", "not_legal", "restricted"})
  void recognizesKnownFreshObservationsEvenWhenNotLegal(String status) {
    assertThat(CardLegality.fresh(definition(status, clock.millis()), "standard", clock)).isTrue();
  }

  @Test
  void missingAndUnrecognizedStatusCannotCountAsVerification() {
    for (String status : Arrays.asList(null, "", "unknown")) {
      assertThat(CardLegality.fresh(definition(status, clock.millis()), "standard", clock))
          .isFalse();
    }
    assertThat(CardLegality.fresh(definition("legal", null), "standard", clock)).isFalse();
    assertThat(CardLegality.fresh(definition("legal", clock.millis()), "commander", clock))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0, 86399999, 86400000, 86400001})
  void freshnessIsStrictlyLessThanTwentyFourHoursAndRejectsFutureTimes(long age) {
    assertThat(CardLegality.fresh(definition("legal", clock.millis() - age), "standard", clock))
        .isEqualTo(age >= 0 && age < 86400000);
  }
}
