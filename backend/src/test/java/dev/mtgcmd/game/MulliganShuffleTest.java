package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MulliganShuffleTest {
  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void redrawShufflesTheCombinedHandAndLibraryBeforeDealing(final GameFormat format) {
    final LifecycleFixture table = new LifecycleFixture(format);
    table.readyAllPlayers();
    table.apply(0, "START", Map.of());
    final List<String> expected = new ArrayList<>();
    GameLookup.zone(table.game, 0, GameZones.LIBRARY)
        .forEach(card -> expected.add(card.definition.id()));
    GameLookup.zone(table.game, 0, GameZones.HAND)
        .forEach(card -> expected.add(card.definition.id()));
    Collections.rotate(expected, -1);
    final SecureRandom random = mock(SecureRandom.class);
    when(random.nextInt(anyInt())).thenReturn(0);
    final MulliganActions actions = new MulliganActions(new CardZoneTransitions(random));
    final long revision = table.game.revision;
    final List<String> log = List.copyOf(table.game.log);
    assertThat(actions.redraw(table.game, 0, Map.of())).isEqualTo("redrew an opening hand");
    assertThat(GameLookup.zone(table.game, 0, GameZones.HAND))
        .extracting(card -> card.definition.id())
        .containsExactlyElementsOf(expected.subList(0, 7));
    assertThat(GameLookup.zone(table.game, 0, GameZones.LIBRARY))
        .extracting(card -> card.definition.id())
        .containsExactlyElementsOf(expected.subList(7, expected.size()));
    assertThat(table.game.revision).isEqualTo(revision);
    assertThat(table.game.log).containsExactlyElementsOf(log);
  }
}
