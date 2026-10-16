import { useCallback, useState, type FormEvent } from "react";
import type { Card, GameFormat } from "./protocol";
import { useTableSession } from "./useTableSession";
import { TableHeader } from "./table/TableHeader";
import { WelcomeScreen } from "./table/WelcomeScreen";
import { LobbyScreen } from "./table/LobbyScreen";
import { PlayingTable } from "./table/PlayingTable";
import { TableOverlays } from "./table/TableOverlays";

export default function App() {
  const [tableFormat, setTableFormat] = useState<GameFormat>("COMMANDER");
  const [firstPlayer, setFirstPlayer] = useState(0);
  const [name, setName] = useState(localStorage.getItem("table-name") || "");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [viewer, setViewer] = useState<{ seat: number; zone: string } | null>(
    null,
  );
  const onNavigate = useCallback(() => {
    setSelectedId(null);
    setViewer(null);
  }, []);
  const session = useTableSession(onNavigate, setFirstPlayer);
  const {
    view,
    room,
    csrf,
    busy,
    socket,
    saved,
    error,
    fail,
    act,
    api,
    loadDraft,
  } = session;
  const disabled =
    !socket.connected || socket.pending || busy || view?.status === "finished";
  const canPlay = !disabled && !view?.players[view.you]?.conceded;
  const inspect = (card: Card) => setSelectedId(card.id);
  const openZone = (seat: number, zone: string) => {
    if (zone === "library") act("SEARCH", { target: seat });
    setViewer({ seat, zone });
  };
  const enter = (event: FormEvent) => {
    event.preventDefault();
    void session.enter(name, tableFormat);
  };
  return (
    <div className="app-shell">
      <TableHeader
        room={room}
        hasView={!!view}
        connected={socket.connected}
        fail={fail}
      />
      {error && (
        <div role="alert" className="error-banner">
          {error}
          <button aria-label="Dismiss error" onClick={() => fail("")}>
            ×
          </button>
        </div>
      )}
      {!view ? (
        <WelcomeScreen
          room={room}
          name={name}
          setName={setName}
          tableFormat={tableFormat}
          setTableFormat={setTableFormat}
          csrf={csrf}
          busy={busy}
          saved={saved}
          enter={enter}
        />
      ) : view.status === "lobby" ? (
        <LobbyScreen
          view={view}
          disabled={disabled}
          firstPlayer={firstPlayer}
          setFirstPlayer={setFirstPlayer}
          act={act}
          preview={api.previewDeck}
          loadDraft={loadDraft}
          fail={fail}
        />
      ) : (
        <PlayingTable
          view={view}
          canPlay={canPlay}
          disabled={disabled}
          act={act}
          inspect={inspect}
          openZone={openZone}
          socketOnline={socket.online}
        />
      )}
      {view?.status === "finished" && (
        <div className="finished-banner">
          Game finished. Your table has been saved.
        </div>
      )}
      {view && (
        <TableOverlays
          view={view}
          viewer={viewer}
          selected={view.cards.find((card) => card.id === selectedId)}
          inspect={inspect}
          act={act}
          canPlay={canPlay}
          closeViewer={() => {
            if (viewer?.zone === "library") act("CLOSE_SEARCH");
            setViewer(null);
          }}
          closeInspector={() => setSelectedId(null)}
        />
      )}
    </div>
  );
}
