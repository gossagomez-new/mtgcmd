package dev.mtgcmd.game;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LifecycleStartupShuffleTest {
  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void startupDealsFromTheShuffledLibraryRatherThanDeckOrder(final GameFormat format) {
    final LifecycleFixture fixture = new LifecycleFixture(format);
    fixture.readyAllPlayers();
    final SecureRandom random = mock(SecureRandom.class);
    when(random.nextInt(anyInt())).thenReturn(0);
    final LifecycleActions actions = new LifecycleActions(new CardZoneTransitions(random));
    final Map<Integer, List<String>> shuffled = new LinkedHashMap<>();
    for (final Game.Player player : fixture.game.players) {
      final List<String> library =
          new ArrayList<>(
              player.deck.stream()
                  .map(Game.Definition::id)
                  .filter(identifier -> !player.commanders.contains(identifier))
                  .toList());
      Collections.rotate(library, -1);
      shuffled.put(player.seat, library);
    }
    final long revision = fixture.game.revision;
    final List<String> log = List.copyOf(fixture.game.log);
    assertThat(actions.start(fixture.game, 0, Map.of())).isEqualTo("start");
    assertThat(fixture.game.status).isEqualTo("opening");
    for (final Game.Player player : fixture.game.players) {
      final List<String> expected = shuffled.get(player.seat);
      assertThat(GameLookup.zone(fixture.game, player.seat, GameZones.HAND))
          .extracting(card -> card.definition.id())
          .containsExactlyElementsOf(expected.subList(0, 7));
      assertThat(GameLookup.zone(fixture.game, player.seat, GameZones.LIBRARY))
          .extracting(card -> card.definition.id())
          .containsExactlyElementsOf(expected.subList(7, expected.size()));
    }
    assertThat(fixture.game.revision).isEqualTo(revision);
    assertThat(fixture.game.log).containsExactlyElementsOf(log);
  }
}
