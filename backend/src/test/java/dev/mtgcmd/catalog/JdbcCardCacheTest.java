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
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final JdbcCardCache cache = new JdbcCardCache(jdbc, mapper);
  private final Definition definition =
      new Definition(
          "id",
          "Island",
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
    when(jdbc.queryForList(anyString(), eq(String.class), eq("island"))).thenReturn(List.of());
    assertThat(cache.find("  ISLAND  ")).isEmpty();
  }

  @Test
  void normalizedLookupRestoresDefinitionAndLegalityMetadata() throws Exception {
    when(jdbc.queryForList(anyString(), eq(String.class), eq("island")))
        .thenReturn(List.of(mapper.writeValueAsString(definition)));
    assertThat(cache.find("  ISLAND  ")).contains(definition);
  }

  @Test
  void storeBindsNormalizedAliasAndCompleteJsonDefinition() throws Exception {
    cache.store("  ISLAND  ", definition);
    var payload = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(anyString(), eq("island"), payload.capture());
    assertThat(mapper.readValue(payload.getValue(), Definition.class)).isEqualTo(definition);
  }

  @Test
  void corruptStoredJsonPreservesTheOriginalCause() {
    when(jdbc.queryForList(anyString(), eq(String.class), eq("island")))
        .thenReturn(List.of("{bad"));
    assertThatThrownBy(() -> cache.find("Island"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid cached card")
        .hasCauseInstanceOf(JsonProcessingException.class);
  }

  @Test
  void failedReadsPropagateWithoutBeingClassifiedAsProviderFailures() {
    var failure = new DataAccessResourceFailureException("read failed");
    when(jdbc.queryForList(anyString(), eq(String.class), eq("island"))).thenThrow(failure);
    assertThat(catchThrowable(() -> cache.find("Island"))).isSameAs(failure);
  }

  @Test
  void failedWritesPreserveExistingServiceMessageAndDatabaseCause() {
    var failure = new DataAccessResourceFailureException("write failed");
    doThrow(failure).when(jdbc).update(anyString(), anyString(), anyString());
    assertThatThrownBy(() -> cache.store("Island", definition))
        .isInstanceOf(CardServiceUnavailableException.class)
        .hasMessage("Card catalog unavailable; try again later")
        .hasCause(failure);
  }

  @Test
  void failedSerializationPreservesCauseAndDoesNotWrite() throws Exception {
    var failingMapper = mock(ObjectMapper.class);
    var failure = new JsonProcessingException("serialize failed") {};
    when(failingMapper.writeValueAsString(definition)).thenThrow(failure);
    var failing = new JdbcCardCache(jdbc, failingMapper);
    assertThatThrownBy(() -> failing.store("Island", definition))
        .isInstanceOf(CardServiceUnavailableException.class)
        .hasCause(failure);
    verifyNoInteractions(jdbc);
  }
}
