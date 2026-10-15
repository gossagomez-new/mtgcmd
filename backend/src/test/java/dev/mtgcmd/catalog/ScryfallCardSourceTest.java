package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.catalog.ScryfallCardSource.LookupResult;
import dev.mtgcmd.catalog.ScryfallCardSource.MatchedCard;
import dev.mtgcmd.game.Game.Definition;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ScryfallCardSourceTest {
  private static final String ISLAND_NAME = "Island";

  private final JsonHttp http = mock(JsonHttp.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(123), ZoneOffset.UTC);
  private final ScryfallCardSource source = new ScryfallCardSource(http, mapper, clock);

  @Test
  void collectionIdentifierAndVerifiedAliasesRemainDistinct() throws Exception {
    when(http.post(any(), anyString()))
        .thenReturn(
            """
        {"data":[{"name":"Clearwater Pathway // Murkwater Pathway","oracle_id":"pathway",
        "card_faces":[{"name":"Clearwater Pathway"},{"name":"Murkwater Pathway"}]}]}
        """);
    final List<String> requested =
        List.of(
            "Clearwater Pathway // Murkwater Pathway",
            " Murkwater Pathway ",
            "Clearwater Pathway // Wrong Back");
    final LookupResult result = source.lookupBatch(requested);
    assertThat(result.supportedEnvelope()).isTrue();
    final MatchedCard card = result.cards().iterator().next();
    assertThat(card.aliases()).containsExactly(requested.get(0), requested.get(1));
    assertThat(card.definition().name()).isEqualTo("Clearwater Pathway // Murkwater Pathway");
    final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(http).post(eq(URI.create("https://api.scryfall.com/cards/collection")), body.capture());
    assertThat(mapper.readTree(body.getValue()).path("identifiers"))
        .isEqualTo(
            mapper.valueToTree(
                List.of(
                    Map.of("name", "Clearwater Pathway"),
                    Map.of("name", " Murkwater Pathway "),
                    Map.of("name", "Clearwater Pathway"))));
  }

  @Test
  void mappingUsesFaceFallbacksAndFiltersUntrustedImages() {
    when(http.post(any(), anyString()))
        .thenReturn(
            """
        {"data":[{"id":"fallback-id","name":"Front // Back","type_line":"Land",
        "color_identity":["U","B"],"legalities":{"commander":"legal","standard":"banned","bad":42},
        "image_uris":{"normal":"https://evil.example/card.jpg"},
        "card_faces":[{"name":"Front","type_line":"Land","oracle_text":"front text",
        "image_uris":{"normal":"https://cards.scryfall.io/front.jpg"}},
        {"name":"Back","oracle_text":"back text","image_uris":{"normal":"http://cards.scryfall.io/back.jpg"}}]}]}
        """);
    final Definition definition =
        source.lookupBatch(List.of("Front // Back")).cards().iterator().next().definition();
    assertThat(definition.id()).isEqualTo("fallback-id");
    assertThat(definition.image()).isEqualTo("https://cards.scryfall.io/front.jpg");
    assertThat(definition.text()).isEqualTo("front text\n\nback text");
    assertThat(definition.faces().get(1).image()).isEmpty();
    assertThat(definition.colors()).containsExactly("U", "B");
    assertThat(definition.legalities())
        .containsExactlyInAnyOrderEntriesOf(Map.of("commander", "legal", "standard", "banned"));
    assertThat(definition.legalityCheckedAt()).isEqualTo(123);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "null", "[]", "{}", "{\"data\":null}", "{\"data\":{}}"})
  void unsupportedEnvelopeIsReportedWithoutApplyingFormatPolicy(final String json) {
    when(http.post(any(), anyString())).thenReturn(json);
    assertThat(source.lookupBatch(List.of(ISLAND_NAME)).supportedEnvelope()).isFalse();
  }

  @Test
  void validEmptyCollectionRemainsSupported() {
    when(http.post(any(), anyString())).thenReturn("{\"data\":[]}");
    final LookupResult result = source.lookupBatch(List.of(ISLAND_NAME));
    assertThat(result.supportedEnvelope()).isTrue();
    assertThat(result.cards()).isEmpty();
  }

  @Test
  void malformedJsonRetainsTypedFailureAndOriginalCause() {
    when(http.post(any(), anyString())).thenReturn("{bad");
    assertThatThrownBy(() -> source.lookupBatch(List.of(ISLAND_NAME)))
        .isInstanceOf(InvalidCardServiceResponseException.class)
        .hasMessage("Card catalog unavailable; try again later")
        .hasCauseInstanceOf(JsonProcessingException.class);
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.MethodSource(
      "dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void operationalFailurePreservesItsIdentity(final CardServiceException failure) {
    when(http.post(any(), anyString())).thenThrow(failure);
    assertThat(catchThrowable(() -> source.lookupBatch(List.of(ISLAND_NAME)))).isSameAs(failure);
  }

  @Test
  void observationAndMappingRemainLazyInProviderOrder() {
    final Clock observations = mock(Clock.class);
    final IllegalStateException failure = new IllegalStateException("second observation failed");
    when(observations.millis()).thenReturn(123L).thenThrow(failure);
    when(http.post(any(), anyString()))
        .thenReturn("{\"data\":[{\"name\":\"Island\"},{\"name\":\"Sol Ring\"}]}");
    final ScryfallCardSource lazySource = new ScryfallCardSource(http, mapper, observations);
    final LookupResult result = lazySource.lookupBatch(List.of(ISLAND_NAME, "Sol Ring"));
    verifyNoInteractions(observations);
    final Iterator<MatchedCard> cards = result.cards().iterator();
    assertThat(cards.next().definition().name()).isEqualTo(ISLAND_NAME);
    assertThat(catchThrowable(cards::next)).isSameAs(failure);
  }

  @Test
  void requestSerializationFailureDoesNotInvokeProvider() throws Exception {
    final ObjectMapper failingMapper = mock(ObjectMapper.class);
    final JsonProcessingException failure = new JsonProcessingException("serialize failed") {};
    when(failingMapper.writeValueAsString(any())).thenThrow(failure);
    final ScryfallCardSource failing = new ScryfallCardSource(http, failingMapper, clock);
    assertThatThrownBy(() -> failing.lookupBatch(List.of(ISLAND_NAME)))
        .isInstanceOf(CardServiceUnavailableException.class)
        .hasCause(failure);
    verifyNoInteractions(http);
  }
}
