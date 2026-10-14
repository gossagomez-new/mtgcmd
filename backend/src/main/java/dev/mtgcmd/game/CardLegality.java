package dev.mtgcmd.game;

import java.time.*;
import java.util.Set;

public final class CardLegality {
  public static final long MAX_AGE_MILLIS = Duration.ofHours(24).toMillis();
  private static final Set<String> STATUSES = Set.of("legal", "not_legal", "banned", "restricted");

  private CardLegality() {}

  public static boolean fresh(Game.Definition card, String format, Clock clock) {
    Long observed = card.legalityCheckedAt();
    long now = clock.millis();
    return observed != null
        && observed <= now
        && observed > now - MAX_AGE_MILLIS
        && STATUSES.contains(card.legalities().getOrDefault(format, ""));
  }
}
