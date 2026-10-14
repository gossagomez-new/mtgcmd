package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.mtgcmd.catalog.*;
import dev.mtgcmd.game.Game;
import dev.mtgcmd.game.GameFormat;
import dev.mtgcmd.store.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class DeckImportServiceTest {
  private final RoomStore rooms = mock(RoomStore.class);
  private final DeckImporter importer = mock(DeckImporter.class);
  private final DeckDraftRepository drafts = mock(DeckDraftRepository.class);
  private final DeckImportService service = new DeckImportService(rooms, importer, drafts);
  private final Game.Definition card =
      new Game.Definition("card", "Card", "", "", "Creature", List.of(), true, List.of());

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void previewAuthorizesThenImportsAndStoresInOrder(GameFormat format) throws Exception {
    when(rooms.format("guest", "room")).thenReturn(format);
    var draft = new DeckImporter.Draft(List.of(card), List.of(card), List.of("card"));
    when(importer.preview("text", "url", format)).thenReturn(draft);
    var result = service.preview("guest", "room", "text", "url");
    UUID.fromString(result.draftId());
    assertThat(result.draft()).isSameAs(draft);
    var order = inOrder(rooms, importer, drafts);
    order.verify(rooms).seat("guest", "room");
    order.verify(rooms).format("guest", "room");
    order.verify(importer).preview("text", "url", format);
    order.verify(drafts).save("guest", result.draftId(), draft.cards());
  }

  @Test
  void unauthorizedPreviewAndLoadStopBeforeFormatOrPersistence() {
    var failure = new RoomStore.Forbidden("no seat");
    when(rooms.seat("guest", "room")).thenThrow(failure);
    assertThatThrownBy(() -> service.preview("guest", "room", "text", "url")).isSameAs(failure);
    assertThatThrownBy(() -> service.load("guest", "room", "draft", null, "cmd", 4))
        .isSameAs(failure);
    verify(rooms, never()).format(anyString(), anyString());
    verifyNoInteractions(importer, drafts);
  }

  @ParameterizedTest
  @MethodSource("dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void providerFailureStopsBeforeDraftStorage(CardServiceException failure) {
    when(rooms.format("guest", "room")).thenReturn(GameFormat.COMMANDER);
    when(importer.preview(any(), any(), any())).thenThrow(failure);
    assertThatThrownBy(() -> service.preview("guest", "room", null, "url")).isSameAs(failure);
    verifyNoInteractions(drafts);
  }

  @Test
  void saveFailureIsNotTranslated() throws Exception {
    when(rooms.format("guest", "room")).thenReturn(GameFormat.STANDARD);
    when(importer.preview(any(), any(), any()))
        .thenReturn(new DeckImporter.Draft(List.of(card), List.of(), List.of()));
    var failure = new JsonProcessingException("broken") {};
    doThrow(failure).when(drafts).save(eq("guest"), anyString(), anyList());
    assertThatThrownBy(() -> service.preview("guest", "room", "text", null)).isSameAs(failure);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void commanderSelectionIsRequiredBeforeDraftLookup(boolean nullSelection) {
    when(rooms.format("guest", "room")).thenReturn(GameFormat.COMMANDER);
    assertThatThrownBy(
            () ->
                service.load("guest", "room", "draft", nullSelection ? null : List.of(), "cmd", 4))
        .hasMessage("Choose commanders");
    verifyNoInteractions(drafts);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void loadPreservesOrderingPayloadAndRetryDelegation(GameFormat format) throws Exception {
    when(rooms.format("guest", "room")).thenReturn(format);
    List<String> commanders = format == GameFormat.STANDARD ? null : List.of("card");
    var cards = List.of(card);
    when(drafts.requireAvailable("guest", "draft")).thenReturn(cards);
    var view = Map.<String, Object>of("revision", 7);
    when(rooms.load("guest", "room", "cmd", 4, cards, commanders)).thenReturn(view);
    for (int retry = 0; retry < 2; retry++)
      assertThat(service.load("guest", "room", "draft", commanders, "cmd", 4)).isSameAs(view);
    var order = inOrder(rooms, drafts);
    for (int retry = 0; retry < 2; retry++) {
      order.verify(rooms).seat("guest", "room");
      order.verify(rooms).format("guest", "room");
      order.verify(drafts).requireAvailable("guest", "draft");
      order.verify(rooms).load("guest", "room", "cmd", 4, cards, commanders);
    }
    verify(drafts, never()).save(anyString(), anyString(), anyList());
  }

  @Test
  void expiredDraftDoesNotReachRoomMutation() throws Exception {
    when(rooms.format("guest", "room")).thenReturn(GameFormat.STANDARD);
    var failure = new IllegalArgumentException("Deck preview expired; preview again");
    when(drafts.requireAvailable("guest", "draft")).thenThrow(failure);
    assertThatThrownBy(() -> service.load("guest", "room", "draft", null, "cmd", 4))
        .isSameAs(failure);
    verify(rooms, never()).load(anyString(), anyString(), anyString(), anyLong(), anyList(), any());
  }

  @Test
  void previewCanBeLoadedInAnotherAuthorizedRoom() throws Exception {
    when(rooms.format("guest", "first")).thenReturn(GameFormat.STANDARD);
    when(rooms.format("guest", "second")).thenReturn(GameFormat.STANDARD);
    var draft = new DeckImporter.Draft(List.of(card), List.of(), List.of());
    when(importer.preview("text", null, GameFormat.STANDARD)).thenReturn(draft);
    var preview = service.preview("guest", "first", "text", null);
    when(drafts.requireAvailable("guest", preview.draftId())).thenReturn(draft.cards());
    var expected = Map.<String, Object>of("revision", 5);
    when(rooms.load("guest", "second", "cmd", 4, draft.cards(), null)).thenReturn(expected);
    assertThat(service.load("guest", "second", preview.draftId(), null, "cmd", 4))
        .isSameAs(expected);
    verify(rooms).seat("guest", "second");
    verify(drafts).requireAvailable("guest", preview.draftId());
  }
}
