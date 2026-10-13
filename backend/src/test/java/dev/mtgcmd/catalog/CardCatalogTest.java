package dev.mtgcmd.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import dev.mtgcmd.game.Game.Definition;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.URI;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class CardCatalogTest {
  private static EmbeddedPostgres pg;
  private static JdbcTemplate jdbc;
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<List<String>> requests = new ArrayList<>();
  private final Map<String, JsonNode> available = new HashMap<>();
  private CardCatalog catalog;

  @BeforeAll
  static void database() throws Exception {
    pg = EmbeddedPostgres.builder().setPort(0).start();
    var dataSource = pg.getPostgresDatabase();
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void close() throws Exception {
    if (pg != null) pg.close();
  }

  @BeforeEach
  void setup() {
    jdbc.execute("DELETE FROM catalog");
    JsonHttp http = mock(JsonHttp.class);
    when(http.post(any(URI.class), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(invocation.<URI>getArgument(0))
                  .isEqualTo(URI.create("https://api.scryfall.com/cards/collection"));
              JsonNode identifiers =
                  mapper.readTree(invocation.<String>getArgument(1)).path("identifiers");
              List<String> names = new ArrayList<>();
              List<JsonNode> data = new ArrayList<>();
              List<Map<String, String>> notFound = new ArrayList<>();
              for (JsonNode identifier : identifiers) {
                String name = identifier.path("name").asText();
                names.add(name);
                JsonNode card = available.get(name.trim().toLowerCase(Locale.ROOT));
                if (card == null) notFound.add(Map.of("name", name));
                else data.add(card);
              }
              requests.add(List.copyOf(names));
              return mapper.writeValueAsString(Map.of("data", data, "not_found", notFound));
            });
    catalog = new CardCatalog(jdbc, mapper, http);
  }

  private void normalCard(String name) {
    available.put(
        name.toLowerCase(Locale.ROOT),
        mapper.valueToTree(
            Map.of("name", name, "oracle_id", name, "legalities", Map.of("commander", "legal"))));
  }

  private void twoFaceCard(String front, String back) {
    JsonNode card =
        mapper.valueToTree(
            Map.of(
                "name",
                front + " // " + back,
                "oracle_id",
                front,
                "color_identity",
                List.of("U", "B"),
                "legalities",
                Map.of("commander", "legal"),
                "card_faces",
                List.of(
                    Map.of(
                        "name",
                        front,
                        "oracle_text",
                        "Front text",
                        "type_line",
                        "Land",
                        "image_uris",
                        Map.of("normal", "https://cards.scryfall.io/front.jpg")),
                    Map.of(
                        "name",
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
      strings = {
        "Clearwater Pathway // Murkwater Pathway",
        "Delver of Secrets // Insectile Aberration",
        "Fire // Ice"
      })
  void fullTwoPartNamesUseFrontIdentifierAndPreserveBothFaces(String name) {
    String[] faces = name.split(" // ");
    twoFaceCard(faces[0], faces[1]);
    Definition card = catalog.resolve(List.of(name)).getFirst();
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
    twoFaceCard("Clearwater Pathway", "Murkwater Pathway");
    var importer =
        new DeckImporter(
            catalog,
            uri ->
                "{\"boards\":{\"mainboard\":{\"cards\":[{\"quantity\":1,\"card\":{\"name\":\"Clearwater Pathway // Murkwater Pathway\"}}]}}}");
    var draft = importer.preview(null, "https://moxfield.com/decks/XFEeHNrHo0Kvm4O1FLDyhQ");
    assertThat(draft.cards()).hasSize(1);
    assertThat(draft.cards().getFirst().faces()).hasSize(2);
  }

  @Test
  void singleFacesNormalCardsAndMixedCachePreserveInputOrder() {
    twoFaceCard("Clearwater Pathway", "Murkwater Pathway");
    normalCard("Island");
    normalCard("Sol Ring");
    Definition island = catalog.resolve(List.of("Island")).getFirst();
    requests.clear();
    var cards =
        catalog.resolve(List.of("Murkwater Pathway", "Island", "Clearwater Pathway", "Sol Ring"));
    assertThat(cards)
        .extracting(Definition::name)
        .containsExactly(
            "Clearwater Pathway // Murkwater Pathway",
            "Island",
            "Clearwater Pathway // Murkwater Pathway",
            "Sol Ring");
    assertThat(cards.get(1)).isEqualTo(island);
    assertThat(requests)
        .containsExactly(List.of("Murkwater Pathway", "Clearwater Pathway", "Sol Ring"));
    catalog.resolve(List.of("murkwater pathway", "clearwater pathway", "Island", "Sol Ring"));
    assertThat(requests).hasSize(1);
    catalog.resolve(List.of("Clearwater Pathway // Murkwater Pathway"));
    assertThat(requests).hasSize(1);
  }

  @Test
  void caseAndSurroundingWhitespaceRemainSupported() {
    twoFaceCard("Clearwater Pathway", "Murkwater Pathway");
    assertThat(catalog.resolve(List.of("  CLEARWATER PATHWAY // MURKWATER PATHWAY  ")))
        .extracting(Definition::name)
        .containsExactly("Clearwater Pathway // Murkwater Pathway");
    assertThat(requests).containsExactly(List.of("CLEARWATER PATHWAY"));
  }

  @Test
  void incorrectBackFaceIsNotSilentlyAcceptedOrCachedAsAlias() {
    twoFaceCard("Clearwater Pathway", "Murkwater Pathway");
    String wrong = "Clearwater Pathway // Wrong Back";
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
        .hasMessage(
            "Unknown card: Unknown // Also Unknown. Check spelling or use the full printed name.");
    assertThatThrownBy(() -> catalog.resolve(List.of("Unknown")))
        .hasMessageContaining("Unknown card: Unknown.");
  }

  @ParameterizedTest
  @ValueSource(ints = {74, 75, 76, 150, 151})
  void collectionBatchBoundariesRetainResolutionOrder(int count) {
    var names = new ArrayList<String>();
    for (int i = 0; i < count; i++) {
      String name = "Front " + i + " // Back " + i;
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
}
