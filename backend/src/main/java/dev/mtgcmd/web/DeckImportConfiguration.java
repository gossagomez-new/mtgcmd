package dev.mtgcmd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mtgcmd.catalog.CardCatalog;
import dev.mtgcmd.catalog.DeckImporter;
import dev.mtgcmd.catalog.JsonHttp;
import dev.mtgcmd.store.DeckDraftRepository;
import dev.mtgcmd.store.RoomStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit composition of provider import, draft persistence, and deck workflow. */
@Configuration
public class DeckImportConfiguration {
  @Bean
  DeckImporter importer(final JdbcTemplate jdbc, final ObjectMapper mapper, final JsonHttp http) {
    return new DeckImporter(new CardCatalog(jdbc, mapper, http), http::get);
  }

  @Bean
  DeckDraftRepository deckDrafts(final JdbcTemplate jdbc, final ObjectMapper mapper) {
    return new DeckDraftRepository(jdbc, mapper);
  }

  @Bean
  DeckImportService deckImports(
      final RoomStore rooms, final DeckImporter importer, final DeckDraftRepository drafts) {
    return new DeckImportService(rooms, importer, drafts);
  }
}
