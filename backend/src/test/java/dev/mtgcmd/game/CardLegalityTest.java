package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CardLegalityTest {
  private static final String LEGAL_STATUS = "legal";

  private static final String STANDARD_FORMAT = "standard";

  private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);

  private Game.Definition definition(final String status, final Long observed) {
    return new Game.Definition(
        "id",
        "Card",
        "",
        "",
        "Creature",
        List.of(),
        true,
        List.of(),
        status == null ? Map.of() : Map.of(STANDARD_FORMAT, status),
        observed);
  }

  @ParameterizedTest
  @ValueSource(strings = {LEGAL_STATUS, "banned", "not_legal", "restricted"})
  void recognizesKnownFreshObservationsEvenWhenNotLegal(final String status) {
    assertThat(CardLegality.fresh(definition(status, clock.millis()), STANDARD_FORMAT, clock))
        .isTrue();
  }

  @Test
  void missingAndUnrecognizedStatusCannotCountAsVerification() {
    for (final String status : Arrays.asList(null, "", "unknown")) {
      assertThat(CardLegality.fresh(definition(status, clock.millis()), STANDARD_FORMAT, clock))
          .isFalse();
    }
    assertThat(CardLegality.fresh(definition(LEGAL_STATUS, null), STANDARD_FORMAT, clock))
        .isFalse();
    assertThat(CardLegality.fresh(definition(LEGAL_STATUS, clock.millis()), "commander", clock))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0, 86399999, 86400000, 86400001})
  void freshnessIsStrictlyLessThanTwentyFourHoursAndRejectsFutureTimes(final long age) {
    assertThat(
            CardLegality.fresh(
                definition(LEGAL_STATUS, clock.millis() - age), STANDARD_FORMAT, clock))
        .isEqualTo(age >= 0 && age < 86400000);
  }
}
