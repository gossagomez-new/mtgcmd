package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.game.Game.Definition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcCardCacheTest {
  private static final String ISLAND_NAME = "Island";

  private static final String ISLAND_KEY = "island";

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final JdbcCardCache cache = new JdbcCardCache(jdbc, mapper);
  private final Definition definition =
      new Definition(
          "id",
          ISLAND_NAME,
          "",
          "",
          "Basic Land",
          List.of("U"),
          true,
          List.of(),
          Map.of("standard", "legal"),
          123L);

  @Test
  void missingEntryIsAnEmptyOptional() {
    when(jdbc.queryForList(anyString(), eq(String.class), eq(ISLAND_KEY))).thenReturn(List.of());
    assertThat(cache.find("  ISLAND  ")).isEmpty();
  }

  @Test
  void normalizedLookupRestoresDefinitionAndLegalityMetadata() throws Exception {
    when(jdbc.queryForList(anyString(), eq(String.class), eq(ISLAND_KEY)))
        .thenReturn(List.of(mapper.writeValueAsString(definition)));
    assertThat(cache.find("  ISLAND  ")).contains(definition);
  }

  @Test
  void storeBindsNormalizedAliasAndCompleteJsonDefinition() throws Exception {
    cache.store("  ISLAND  ", definition);
    final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(anyString(), eq(ISLAND_KEY), payload.capture());
    assertThat(mapper.readValue(payload.getValue(), Definition.class)).isEqualTo(definition);
  }

  @Test
  void corruptStoredJsonPreservesTheOriginalCause() {
    when(jdbc.queryForList(anyString(), eq(String.class), eq(ISLAND_KEY)))
        .thenReturn(List.of("{bad"));
    assertThatThrownBy(() -> cache.find(ISLAND_NAME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid cached card")
        .hasCauseInstanceOf(JsonProcessingException.class);
  }

  @Test
  void failedReadsPropagateWithoutBeingClassifiedAsProviderFailures() {
    final DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("read failed");
    when(jdbc.queryForList(anyString(), eq(String.class), eq(ISLAND_KEY))).thenThrow(failure);
    assertThat(catchThrowable(() -> cache.find(ISLAND_NAME))).isSameAs(failure);
  }

  @Test
  void failedWritesPreserveExistingServiceMessageAndDatabaseCause() {
    final DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("write failed");
    doThrow(failure).when(jdbc).update(anyString(), anyString(), anyString());
    assertThatThrownBy(() -> cache.store(ISLAND_NAME, definition))
        .isInstanceOf(CardServiceUnavailableException.class)
        .hasMessage("Card catalog unavailable; try again later")
        .hasCause(failure);
  }

  @Test
  void failedSerializationPreservesCauseAndDoesNotWrite() throws Exception {
    final ObjectMapper failingMapper = mock(ObjectMapper.class);
    final JsonProcessingException failure = new JsonProcessingException("serialize failed") {};
    when(failingMapper.writeValueAsString(definition)).thenThrow(failure);
    final JdbcCardCache failing = new JdbcCardCache(jdbc, failingMapper);
    assertThatThrownBy(() -> failing.store(ISLAND_NAME, definition))
        .isInstanceOf(CardServiceUnavailableException.class)
        .hasCause(failure);
    verifyNoInteractions(jdbc);
  }
}
