package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.*;
import dev.mtgcmd.catalog.DeckImporter.Draft;
import dev.mtgcmd.game.Game.Definition;
import dev.mtgcmd.game.GameFormat;
import dev.mtgcmd.testsupport.LogCapture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.URI;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class CardCatalogTest {
  private static final String PATHWAY_FRONT = "Clearwater Pathway";

  private static final String PATHWAY_FULL_NAME = "Clearwater Pathway // Murkwater Pathway";

  private static final String ISLAND_NAME = "Island";

  private static final String PATHWAY_BACK_NAME = "Murkwater Pathway";

  private static final String SOL_RING_NAME = "Sol Ring";

  private static final String UNKNOWN_CARD_NAME = "Unknown";

  private static final String COMMANDER_FORMAT = "commander";

  private static final String LEGAL_STATUS = "legal";

  private static final String LEGALITIES_FIELD = "legalities";

  private static final String NAME_FIELD = "name";

  private static final String NOT_LEGAL_STATUS = "not_legal";

  private static final String STANDARD_FORMAT = "standard";

  private static EmbeddedPostgres postgres;
  private static JdbcTemplate jdbc;
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<List<String>> requests = new ArrayList<>();
  private final Map<String, JsonNode> available = new HashMap<>();
  private CardCatalog catalog;
  private JsonHttp http;
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);

  @BeforeAll
  static void database() throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(0).start();
    final DataSource dataSource = postgres.getPostgresDatabase();
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void close() throws Exception {
    if (postgres != null) {
      postgres.close();
    }
  }

  @BeforeEach
  void setup() {
    jdbc.execute("DELETE FROM catalog");
    http = mock(JsonHttp.class);
    when(http.post(any(URI.class), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(invocation.<URI>getArgument(0))
                  .isEqualTo(URI.create("https://api.scryfall.com/cards/collection"));
              final JsonNode identifiers =
                  mapper.readTree(invocation.<String>getArgument(1)).path("identifiers");
              final List<String> names = new ArrayList<>();
              final List<JsonNode> data = new ArrayList<>();
              final List<Map<String, String>> notFound = new ArrayList<>();
              for (final JsonNode identifier : identifiers) {
                final String name = identifier.path(NAME_FIELD).asText();
                names.add(name);
                final JsonNode card = available.get(name.trim().toLowerCase(Locale.ROOT));
                if (card == null) {
                  notFound.add(Map.of(NAME_FIELD, name));
                } else {
                  data.add(card);
                }
              }
              requests.add(List.copyOf(names));
              return mapper.writeValueAsString(Map.of("data", data, "not_found", notFound));
            });
    catalog = new CardCatalog(jdbc, mapper, http);
  }

  private void normalCard(final String name) {
    available.put(
        name.toLowerCase(Locale.ROOT),
        mapper.valueToTree(
            Map.of(
                NAME_FIELD,
                name,
                "oracle_id",
                name,
                LEGALITIES_FIELD,
                Map.of(COMMANDER_FORMAT, LEGAL_STATUS))));
  }

  private void twoFaceCard(final String front, final String back) {
    final JsonNode card =
        mapper.valueToTree(
            Map.of(
                NAME_FIELD,
                front + " // " + back,
                "oracle_id",
                front,
                "color_identity",
                List.of("U", "B"),
                LEGALITIES_FIELD,
                Map.of(COMMANDER_FORMAT, LEGAL_STATUS),
                "card_faces",
                List.of(
                    Map.of(
                        NAME_FIELD,
                        front,
                        "oracle_text",
                        "Front text",
                        "type_line",
                        "Land",
                        "image_uris",
                        Map.of("normal", "https://cards.scryfall.io/front.jpg")),
                    Map.of(
                        NAME_FIELD,
                        back,
                        "oracle_text",
                        "Back text",
                        "type_line",
                        "Land",
                        "image_uris",
                        Map.of("normal", "https://cards.scryfall.io/back.jpg")))));
    available.put(front.toLowerCase(Locale.ROOT), card);
    available.put(back.toLowerCase(Locale.ROOT), card);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {PATHWAY_FULL_NAME, "Delver of Secrets // Insectile Aberration", "Fire // Ice"})
  void fullTwoPartNamesUseFrontIdentifierAndPreserveBothFaces(final String name) {
    final String[] faces = name.split(" // ");
    twoFaceCard(faces[0], faces[1]);
    final Definition card = catalog.resolve(List.of(name)).getFirst();
    assertThat(requests).containsExactly(List.of(faces[0]));
    assertThat(card.name()).isEqualTo(name);
    assertThat(card.faces()).extracting(face -> face.name()).containsExactly(faces);
    assertThat(card.faces())
        .extracting(face -> face.text())
        .containsExactly("Front text", "Back text");
    assertThat(card.faces())
        .extracting(face -> face.image())
        .containsExactly(
            "https://cards.scryfall.io/front.jpg", "https://cards.scryfall.io/back.jpg");
    assertThat(card.colors()).containsExactly("U", "B");
    assertThat(card.legal()).isTrue();
    assertThat(catalog.resolve(List.of(name))).containsExactly(card);
    assertThat(requests).hasSize(1);
  }

  @Test
  void moxfieldPreviewResolvesTheReportedCard() {
    twoFaceCard(PATHWAY_FRONT, PATHWAY_BACK_NAME);
    final DeckImporter importer =
        new DeckImporter(
            catalog,
            uri ->
                "{\"boards\":{\"mainboard\":{\"cards\":[{\"quantity\":1,\"card\":{\"name\":\"Clearwater Pathway // Murkwater Pathway\"}}]}}}");
    final Draft draft = importer.preview(null, "https://moxfield.com/decks/XFEeHNrHo0Kvm4O1FLDyhQ");
    assertThat(draft.cards()).hasSize(1);
    assertThat(draft.cards().getFirst().faces()).hasSize(2);
  }

  @Test
  void singleFacesNormalCardsAndMixedCachePreserveInputOrder() {
    twoFaceCard(PATHWAY_FRONT, PATHWAY_BACK_NAME);
    normalCard(ISLAND_NAME);
    normalCard(SOL_RING_NAME);
    final Definition island = catalog.resolve(List.of(ISLAND_NAME)).getFirst();
    requests.clear();
    final List<Definition> cards =
        catalog.resolve(List.of(PATHWAY_BACK_NAME, ISLAND_NAME, PATHWAY_FRONT, SOL_RING_NAME));
    assertThat(cards)
        .extracting(Definition::name)
        .containsExactly(PATHWAY_FULL_NAME, ISLAND_NAME, PATHWAY_FULL_NAME, SOL_RING_NAME);
    assertThat(cards.get(1)).isEqualTo(island);
    assertThat(requests).containsExactly(List.of(PATHWAY_BACK_NAME, PATHWAY_FRONT, SOL_RING_NAME));
    catalog.resolve(List.of("murkwater pathway", "clearwater pathway", ISLAND_NAME, SOL_RING_NAME));
    assertThat(requests).hasSize(1);
    catalog.resolve(List.of(PATHWAY_FULL_NAME));
    assertThat(requests).hasSize(1);
  }

  @Test
  void caseAndSurroundingWhitespaceRemainSupported() {
    twoFaceCard(PATHWAY_FRONT, PATHWAY_BACK_NAME);
    assertThat(catalog.resolve(List.of("  CLEARWATER PATHWAY // MURKWATER PATHWAY  ")))
        .extracting(Definition::name)
        .containsExactly(PATHWAY_FULL_NAME);
    assertThat(requests).containsExactly(List.of("CLEARWATER PATHWAY"));
  }

  @Test
  void incorrectBackFaceIsNotSilentlyAcceptedOrCachedAsAlias() {
    twoFaceCard(PATHWAY_FRONT, PATHWAY_BACK_NAME);
    final String wrong = "Clearwater Pathway // Wrong Back";
    assertThatThrownBy(() -> catalog.resolve(List.of(wrong)))
        .hasMessage("Unknown card: " + wrong + ". Check spelling or use the full printed name.");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM catalog WHERE name=?",
                Integer.class,
                wrong.toLowerCase(Locale.ROOT)))
        .isZero();
  }

  @Test
  void unknownCardsKeepTheirOriginalErrorName() {
    assertThatThrownBy(() -> catalog.resolve(List.of("Unknown // Also Unknown")))
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Unknown card: Unknown // Also Unknown. Check spelling or use the full printed name.");
    assertThatThrownBy(() -> catalog.resolve(List.of(UNKNOWN_CARD_NAME)))
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown card: Unknown.");
  }

  @ParameterizedTest
  @ValueSource(ints = {74, 75, 76, 150, 151})
  void collectionBatchBoundariesRetainResolutionOrder(final int count) {
    final List<String> names = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final String name = "Front " + i + " // Back " + i;
      names.add(name);
      twoFaceCard("Front " + i, "Back " + i);
    }
    assertThat(catalog.resolve(names))
        .extracting(Definition::name)
        .containsExactlyElementsOf(names);
    assertThat(requests).hasSize((count + 74) / 75);
    assertThat(requests).allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(75));
    assertThat(requests.stream().flatMap(List::stream).toList())
        .containsExactlyElementsOf(names.stream().map(name -> name.split(" // ")[0]).toList());
  }

  private void cached(final String name, final String status, final Long checkedAt)
      throws Exception {
    final Definition def =
        new Definition(
            name,
            name,
            "",
            "",
            "Basic Land",
            List.of(),
            true,
            List.of(),
            status == null ? Map.of() : Map.of(STANDARD_FORMAT, status),
            checkedAt);
    jdbc.update(
        "INSERT INTO catalog(name,definition) VALUES (?,CAST(? AS jsonb))",
        name.toLowerCase(Locale.ROOT),
        mapper.writeValueAsString(def));
  }

  private void standardCard(final String name, final String status) {
    normalCard(name);
    ((com.fasterxml.jackson.databind.node.ObjectNode) available.get(name.toLowerCase(Locale.ROOT)))
        .set(
            LEGALITIES_FIELD,
            mapper.valueToTree(Map.of(COMMANDER_FORMAT, LEGAL_STATUS, STANDARD_FORMAT, status)));
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0, 86399999, 86400000, 86400001})
  void standardLegalityFreshnessHasDeterministicBoundaries(final long age) throws Exception {
    cached(ISLAND_NAME, LEGAL_STATUS, clock.millis() - age);
    standardCard(ISLAND_NAME, LEGAL_STATUS);
    catalog = new CardCatalog(jdbc, mapper, http, clock);
    final Definition def = catalog.resolve(List.of(ISLAND_NAME), GameFormat.STANDARD).getFirst();
    final boolean refresh = age < 0 || age >= 86400000;
    assertThat(requests).hasSize(refresh ? 1 : 0);
    assertThat(def.legalityCheckedAt()).isEqualTo(refresh ? clock.millis() : clock.millis() - age);
    assertThat(def.legalities()).containsEntry(STANDARD_FORMAT, LEGAL_STATUS);
  }

  @Test
  void missingMetadataRefreshesOnlyForStandardAndCachesObservation() throws Exception {
    cached(ISLAND_NAME, null, null);
    standardCard(ISLAND_NAME, "banned");
    catalog = new CardCatalog(jdbc, mapper, http, clock);
    assertThat(catalog.resolve(List.of(ISLAND_NAME)).getFirst().legalities()).isEmpty();
    assertThat(requests).isEmpty();
    final Definition def = catalog.resolve(List.of(ISLAND_NAME), GameFormat.STANDARD).getFirst();
    assertThat(def.legalities())
        .containsEntry(STANDARD_FORMAT, "banned")
        .containsEntry(COMMANDER_FORMAT, LEGAL_STATUS);
    assertThat(def.legalityCheckedAt()).isEqualTo(clock.millis());
    assertThat(catalog.resolve(List.of(ISLAND_NAME), GameFormat.STANDARD)).containsExactly(def);
    assertThat(requests).hasSize(1);
  }

  @Test
  void refreshFailurePreservesCachedCardsButDoesNotInventUncachedDefinitions() throws Exception {
    cached(ISLAND_NAME, LEGAL_STATUS, clock.millis() - 86400000);
    catalog = new CardCatalog(jdbc, mapper, http, clock);
    when(http.post(any(URI.class), anyString()))
        .thenThrow(new CardServiceUnavailableException("Catalog offline", null));
    final Definition fallback =
        catalog.resolve(List.of(ISLAND_NAME), GameFormat.STANDARD).getFirst();
    assertThat(fallback.legalityCheckedAt()).isEqualTo(clock.millis() - 86400000);
    assertThatThrownBy(
            () -> catalog.resolve(List.of(ISLAND_NAME, UNKNOWN_CARD_NAME), GameFormat.STANDARD))
        .hasMessageContaining("offline");
  }

  @Test
  void partialResponseRetainsStaleCacheAndRejectsUncachedUnknownNames() throws Exception {
    cached(ISLAND_NAME, null, null);
    standardCard(SOL_RING_NAME, NOT_LEGAL_STATUS);
    catalog = new CardCatalog(jdbc, mapper, http, clock);
    final List<Definition> definitions =
        catalog.resolve(List.of(ISLAND_NAME, SOL_RING_NAME), GameFormat.STANDARD);
    assertThat(definitions)
        .extracting(Definition::name)
        .containsExactly(ISLAND_NAME, SOL_RING_NAME);
    assertThat(definitions.getFirst().legalityCheckedAt()).isNull();
    assertThat(definitions.get(1).legalities()).containsEntry(STANDARD_FORMAT, NOT_LEGAL_STATUS);
    assertThatThrownBy(
            () -> catalog.resolve(List.of(ISLAND_NAME, UNKNOWN_CARD_NAME), GameFormat.STANDARD))
        .hasMessageContaining("Unknown card: Unknown");
  }

  @Test
  void multiFaceStandardLookupKeepsMetadataAndBothFaces() {
    twoFaceCard(PATHWAY_FRONT, PATHWAY_BACK_NAME);
    ((com.fasterxml.jackson.databind.node.ObjectNode) available.get("clearwater pathway"))
        .set(
            LEGALITIES_FIELD,
            mapper.valueToTree(
                Map.of(COMMANDER_FORMAT, LEGAL_STATUS, STANDARD_FORMAT, NOT_LEGAL_STATUS)));
    catalog = new CardCatalog(jdbc, mapper, http, clock);
    final Definition def =
        catalog.resolve(List.of(PATHWAY_FULL_NAME), GameFormat.STANDARD).getFirst();
    assertThat(def.faces()).hasSize(2);
    assertThat(def.legalities()).containsEntry(STANDARD_FORMAT, NOT_LEGAL_STATUS);
    assertThat(def.legalityCheckedAt()).isEqualTo(clock.millis());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "null", "[]", "{bad", "{}", "{\"data\":null}"})
  void malformedRefreshResponsesFallBackOnlyToCachedDefinitions(final String response)
      throws Exception {
    cached(ISLAND_NAME, null, null);
    when(http.post(any(URI.class), anyString())).thenReturn(response);
    catalog = new CardCatalog(jdbc, mapper, http, clock);
    assertThat(
            catalog
                .resolve(List.of(ISLAND_NAME), GameFormat.STANDARD)
                .getFirst()
                .legalityCheckedAt())
        .isNull();
    assertThatThrownBy(() -> catalog.resolve(List.of("Uncached"), GameFormat.STANDARD))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cachedMultiFaceAliasesRefreshTogetherAndReuseTheirObservation() throws Exception {
    final String name = PATHWAY_FULL_NAME;
    cached(name, null, null);
    cached(PATHWAY_FRONT, null, null);
    twoFaceCard(PATHWAY_FRONT, PATHWAY_BACK_NAME);
    ((com.fasterxml.jackson.databind.node.ObjectNode) available.get("clearwater pathway"))
        .set(
            LEGALITIES_FIELD,
            mapper.valueToTree(
                Map.of(COMMANDER_FORMAT, LEGAL_STATUS, STANDARD_FORMAT, LEGAL_STATUS)));
    catalog = new CardCatalog(jdbc, mapper, http, clock);
    final List<Definition> cards =
        catalog.resolve(List.of(name, PATHWAY_FRONT), GameFormat.STANDARD);
    assertThat(cards).hasSize(2);
    assertThat(cards)
        .allSatisfy(card -> assertThat(card.legalityCheckedAt()).isEqualTo(clock.millis()));
    assertThat(cards.get(0)).isEqualTo(cards.get(1));
    catalog.resolve(List.of(name, PATHWAY_FRONT), GameFormat.STANDARD);
    assertThat(requests).hasSize(1);
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.MethodSource(
      "dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void refreshRecoveryLogsKnownFailuresButNeverSwallowsInterruption(
      final CardServiceException failure) throws Exception {
    cached(ISLAND_NAME, LEGAL_STATUS, clock.millis() - 86400000);
    when(http.post(any(URI.class), anyString())).thenThrow(failure);
    catalog = new CardCatalog(jdbc, mapper, http, clock);
    try (LogCapture logs = new LogCapture(CardCatalog.class)) {
      if (failure instanceof CardServiceInterruptedException) {
        Thread.currentThread().interrupt();
        assertThat(catchThrowable(() -> catalog.resolve(List.of(ISLAND_NAME), GameFormat.STANDARD)))
            .isSameAs(failure);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(logs.events()).isEmpty();
      } else {
        assertThat(
                catalog
                    .resolve(List.of(ISLAND_NAME), GameFormat.STANDARD)
                    .getFirst()
                    .legalityCheckedAt())
            .isEqualTo(clock.millis() - 86400000);
        assertThat(logs.events()).hasSize(1);
        assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.events().getFirst().getThrowableProxy().getClassName())
            .isEqualTo(failure.getClass().getName());
        assertThat(logs.events().getFirst().getFormattedMessage()).doesNotContain(ISLAND_NAME);
      }
    } finally {
      Thread.interrupted();
    }
    try (LogCapture logs = new LogCapture(CardCatalog.class)) {
      assertThat(
              catchThrowable(
                  () ->
                      catalog.resolve(
                          List.of(ISLAND_NAME, UNKNOWN_CARD_NAME), GameFormat.STANDARD)))
          .isSameAs(failure);
      assertThat(logs.events()).isEmpty();
    }
  }

  @Test
  void malformedCatalogJsonRetainsItsCauseAndExistingMessage() {
    when(http.post(any(URI.class), anyString())).thenReturn("{bad");
    assertThatThrownBy(() -> catalog.resolve(List.of(ISLAND_NAME)))
        .isInstanceOf(InvalidCardServiceResponseException.class)
        .hasMessage("Card catalog unavailable; try again later")
        .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
  }

  @Test
  void programmingFailureDoesNotActivateCachedRefreshFallback() throws Exception {
    cached(ISLAND_NAME, null, null);
    final NullPointerException bug = new NullPointerException("provider adapter bug");
    when(http.post(any(URI.class), anyString())).thenThrow(bug);
    catalog = new CardCatalog(jdbc, mapper, http, clock);
    assertThat(catchThrowable(() -> catalog.resolve(List.of(ISLAND_NAME), GameFormat.STANDARD)))
        .isSameAs(bug);
  }

  @Test
  void repeatedNamesPreserveOutputMultiplicityWithoutRepeatingProviderIdentifiers() {
    normalCard(ISLAND_NAME);
    normalCard(SOL_RING_NAME);
    assertThat(catalog.resolve(List.of(ISLAND_NAME, SOL_RING_NAME, ISLAND_NAME)))
        .extracting(Definition::name)
        .containsExactly(ISLAND_NAME, SOL_RING_NAME, ISLAND_NAME);
    assertThat(requests).containsExactly(List.of(ISLAND_NAME, SOL_RING_NAME));
  }

  @Test
  void unresolvedFirstBatchStopsBeforeLaterRequestsButRetainsSuccessfulCacheWrites() {
    final List<String> names = new ArrayList<>();
    names.add(UNKNOWN_CARD_NAME);
    for (int i = 0; i < 75; i++) {
      final String name = "Card " + i;
      names.add(name);
      normalCard(name);
    }
    assertThatThrownBy(() -> catalog.resolve(names)).hasMessageContaining("Unknown card: Unknown");
    assertThat(requests).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog", Integer.class)).isEqualTo(74);
  }

  @Test
  void failureMappingLaterCardRetainsEarlierCacheWrites() {
    normalCard(ISLAND_NAME);
    normalCard(SOL_RING_NAME);
    final Clock observationClock = mock(Clock.class);
    final IllegalStateException failure = new IllegalStateException("clock failed");
    when(observationClock.millis()).thenReturn(123L).thenThrow(failure);
    catalog = new CardCatalog(jdbc, mapper, http, observationClock);
    assertThat(catchThrowable(() -> catalog.resolve(List.of(ISLAND_NAME, SOL_RING_NAME))))
        .isSameAs(failure);
    assertThat(jdbc.queryForList("SELECT name FROM catalog", String.class))
        .containsExactly("island");
  }

  @Test
  void failedAliasWriteIsNotTreatedAsRecoverableProviderFailure() throws Exception {
    final String alias = PATHWAY_BACK_NAME;
    cached(alias, null, null);
    twoFaceCard(PATHWAY_FRONT, alias);
    final JdbcTemplate failingJdbc = spy(jdbc);
    final DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("write failed");
    doThrow(failure)
        .when(failingJdbc)
        .update(anyString(), eq(alias.toLowerCase(Locale.ROOT)), anyString());
    catalog = new CardCatalog(failingJdbc, mapper, http, clock);
    try (LogCapture logs = new LogCapture(CardCatalog.class)) {
      assertThatThrownBy(() -> catalog.resolve(List.of(alias), GameFormat.STANDARD))
          .isInstanceOf(CardServiceUnavailableException.class)
          .hasCause(failure);
      assertThat(logs.events()).isEmpty();
    }
    assertThat(jdbc.queryForList("SELECT name FROM catalog", String.class))
        .contains("clearwater pathway // murkwater pathway", "murkwater pathway");
  }

  @Test
  void commanderKeepsPermissiveEnvelopeHandlingWhileStandardRequiresAnArray() {
    when(http.post(any(URI.class), anyString()))
        .thenReturn("{\"data\":{\"entry\":{\"name\":\"Island\"}}}");
    assertThat(catalog.resolve(List.of(ISLAND_NAME)))
        .extracting(Definition::name)
        .containsExactly(ISLAND_NAME);
    jdbc.execute("DELETE FROM catalog");
    assertThatThrownBy(() -> catalog.resolve(List.of(ISLAND_NAME), GameFormat.STANDARD))
        .isInstanceOf(InvalidCardServiceResponseException.class)
        .hasMessage("Card catalog returned an unsupported response");
  }
}
