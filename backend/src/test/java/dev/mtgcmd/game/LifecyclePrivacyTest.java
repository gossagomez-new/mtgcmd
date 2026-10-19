package dev.mtgcmd.game;

import static dev.mtgcmd.game.LifecycleFixture.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LifecyclePrivacyTest {
  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void concessionRemovesBothDirectionsOfGrantsAndOnlyTheConcedingSearch(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.startGame();
    fixture.game.grants.put(0, new HashSet<>(Set.of(1)));
    fixture.game.grants.put(1, new HashSet<>(Set.of(0, 1)));
    fixture.game.searches.put(0, 1);
    fixture.game.searches.put(1, 1);
    final String handId = GameLookup.zone(fixture.game, 0, GameZones.HAND).getFirst().id;
    assertThat(fixture.view(1).toString()).contains(handId);
    assertThat(fixture.view(0).path("players").get(1).path("privateAccess").asBoolean()).isTrue();
    fixture.apply(0, CONCEDE, Map.of());
    assertThat(fixture.game.grants).containsOnlyKeys(1);
    assertThat(fixture.game.grants.get(1)).containsExactly(1);
    assertThat(fixture.game.searches).containsExactlyEntriesOf(Map.of(1, 1));
    assertThat(fixture.view(1).toString()).doesNotContain(handId);
    assertThat(fixture.view(0).path("players").get(1).path("privateAccess").asBoolean()).isFalse();
    assertThat(fixture.view(1).path("search").asInt()).isEqualTo(1);
  }
}
