package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.mtgcmd.catalog.*;
import dev.mtgcmd.catalog.DeckImporter.Draft;
import dev.mtgcmd.game.Game;
import dev.mtgcmd.game.GameFormat;
import dev.mtgcmd.store.*;
import dev.mtgcmd.store.RoomStore.Forbidden;
import dev.mtgcmd.web.DeckImportService.StoredPreview;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.InOrder;

class DeckImportServiceTest {
  private static final String COMMAND_ID = "cmd";

  private static final String DRAFT_ID = "draft";

  private static final String GUEST_ID = "guest";

  private static final String ROOM_ID = "room";

  private static final String SECOND_ROOM_ID = "second";

  private static final String DECK_TEXT = "text";

  private static final String DECK_URL = "url";

  private final RoomStore rooms = mock(RoomStore.class);
  private final DeckImporter importer = mock(DeckImporter.class);
  private final DeckDraftRepository drafts = mock(DeckDraftRepository.class);
  private final DeckImportService service = new DeckImportService(rooms, importer, drafts);
  private final Game.Definition card =
      new Game.Definition("card", "Card", "", "", "Creature", List.of(), true, List.of());

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void previewAuthorizesThenImportsAndStoresInOrder(final GameFormat format) throws Exception {
    when(rooms.format(GUEST_ID, ROOM_ID)).thenReturn(format);
    final Draft draft = new DeckImporter.Draft(List.of(card), List.of(card), List.of("card"));
    when(importer.preview(DECK_TEXT, DECK_URL, format)).thenReturn(draft);
    final StoredPreview result = service.preview(GUEST_ID, ROOM_ID, DECK_TEXT, DECK_URL);
    UUID.fromString(result.draftId());
    assertThat(result.draft()).isSameAs(draft);
    final InOrder order = inOrder(rooms, importer, drafts);
    order.verify(rooms).seat(GUEST_ID, ROOM_ID);
    order.verify(rooms).format(GUEST_ID, ROOM_ID);
    order.verify(importer).preview(DECK_TEXT, DECK_URL, format);
    order.verify(drafts).save(GUEST_ID, result.draftId(), draft.cards());
  }

  @Test
  void unauthorizedPreviewAndLoadStopBeforeFormatOrPersistence() {
    final Forbidden failure = new RoomStore.Forbidden("no seat");
    when(rooms.seat(GUEST_ID, ROOM_ID)).thenThrow(failure);
    assertThatThrownBy(() -> service.preview(GUEST_ID, ROOM_ID, DECK_TEXT, DECK_URL))
        .isSameAs(failure);
    assertThatThrownBy(() -> service.load(GUEST_ID, ROOM_ID, DRAFT_ID, null, COMMAND_ID, 4))
        .isSameAs(failure);
    verify(rooms, never()).format(anyString(), anyString());
    verifyNoInteractions(importer, drafts);
  }

  @ParameterizedTest
  @MethodSource("dev.mtgcmd.catalog.DeckImporterTest#serviceFailures")
  void providerFailureStopsBeforeDraftStorage(final CardServiceException failure) {
    when(rooms.format(GUEST_ID, ROOM_ID)).thenReturn(GameFormat.COMMANDER);
    when(importer.preview(any(), any(), any())).thenThrow(failure);
    assertThatThrownBy(() -> service.preview(GUEST_ID, ROOM_ID, null, DECK_URL)).isSameAs(failure);
    verifyNoInteractions(drafts);
  }

  @Test
  void saveFailureIsNotTranslated() throws Exception {
    when(rooms.format(GUEST_ID, ROOM_ID)).thenReturn(GameFormat.STANDARD);
    when(importer.preview(any(), any(), any()))
        .thenReturn(new DeckImporter.Draft(List.of(card), List.of(), List.of()));
    final JsonProcessingException failure = new JsonProcessingException("broken") {};
    doThrow(failure).when(drafts).save(eq(GUEST_ID), anyString(), anyList());
    assertThatThrownBy(() -> service.preview(GUEST_ID, ROOM_ID, DECK_TEXT, null)).isSameAs(failure);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void commanderSelectionIsRequiredBeforeDraftLookup(final boolean nullSelection) {
    when(rooms.format(GUEST_ID, ROOM_ID)).thenReturn(GameFormat.COMMANDER);
    assertThatThrownBy(
            () ->
                service.load(
                    GUEST_ID, ROOM_ID, DRAFT_ID, nullSelection ? null : List.of(), COMMAND_ID, 4))
        .hasMessage("Choose commanders");
    verifyNoInteractions(drafts);
  }

  @ParameterizedTest
  @EnumSource(GameFormat.class)
  void loadPreservesOrderingPayloadAndRetryDelegation(final GameFormat format) throws Exception {
    when(rooms.format(GUEST_ID, ROOM_ID)).thenReturn(format);
    final List<String> commanders = format == GameFormat.STANDARD ? null : List.of("card");
    final List<Game.Definition> cards = List.of(card);
    when(drafts.requireAvailable(GUEST_ID, DRAFT_ID)).thenReturn(cards);
    final Map<String, Object> view = Map.<String, Object>of("revision", 7);
    when(rooms.load(GUEST_ID, ROOM_ID, COMMAND_ID, 4, cards, commanders)).thenReturn(view);
    for (int retry = 0; retry < 2; retry++) {
      assertThat(service.load(GUEST_ID, ROOM_ID, DRAFT_ID, commanders, COMMAND_ID, 4))
          .isSameAs(view);
    }
    final InOrder order = inOrder(rooms, drafts);
    for (int retry = 0; retry < 2; retry++) {
      order.verify(rooms).seat(GUEST_ID, ROOM_ID);
      order.verify(rooms).format(GUEST_ID, ROOM_ID);
      order.verify(drafts).requireAvailable(GUEST_ID, DRAFT_ID);
      order.verify(rooms).load(GUEST_ID, ROOM_ID, COMMAND_ID, 4, cards, commanders);
    }
    verify(drafts, never()).save(anyString(), anyString(), anyList());
  }

  @Test
  void expiredDraftDoesNotReachRoomMutation() throws Exception {
    when(rooms.format(GUEST_ID, ROOM_ID)).thenReturn(GameFormat.STANDARD);
    final IllegalArgumentException failure =
        new IllegalArgumentException("Deck preview expired; preview again");
    when(drafts.requireAvailable(GUEST_ID, DRAFT_ID)).thenThrow(failure);
    assertThatThrownBy(() -> service.load(GUEST_ID, ROOM_ID, DRAFT_ID, null, COMMAND_ID, 4))
        .isSameAs(failure);
    verify(rooms, never()).load(anyString(), anyString(), anyString(), anyLong(), anyList(), any());
  }

  @Test
  void previewCanBeLoadedInAnotherAuthorizedRoom() throws Exception {
    when(rooms.format(GUEST_ID, "first")).thenReturn(GameFormat.STANDARD);
    when(rooms.format(GUEST_ID, SECOND_ROOM_ID)).thenReturn(GameFormat.STANDARD);
    final Draft draft = new DeckImporter.Draft(List.of(card), List.of(), List.of());
    when(importer.preview(DECK_TEXT, null, GameFormat.STANDARD)).thenReturn(draft);
    final StoredPreview preview = service.preview(GUEST_ID, "first", DECK_TEXT, null);
    when(drafts.requireAvailable(GUEST_ID, preview.draftId())).thenReturn(draft.cards());
    final Map<String, Object> expected = Map.<String, Object>of("revision", 5);
    when(rooms.load(GUEST_ID, SECOND_ROOM_ID, COMMAND_ID, 4, draft.cards(), null))
        .thenReturn(expected);
    assertThat(service.load(GUEST_ID, SECOND_ROOM_ID, preview.draftId(), null, COMMAND_ID, 4))
        .isSameAs(expected);
    verify(rooms).seat(GUEST_ID, SECOND_ROOM_ID);
    verify(drafts).requireAvailable(GUEST_ID, preview.draftId());
  }
}
