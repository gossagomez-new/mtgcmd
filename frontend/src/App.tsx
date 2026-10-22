import { useCallback, useEffect, useState, type ComponentProps } from "react";
import type { Card, GameFormat, Snapshot } from "./protocol";
import { useTableSession } from "./useTableSession";
import { TableHeader } from "./table/TableHeader";
import { WelcomeScreen } from "./table/WelcomeScreen";
import { LobbyScreen } from "./table/LobbyScreen";
import { PlayingTable } from "./table/PlayingTable";
import { OpeningHands } from "./table/OpeningHands";
import { TableOverlays } from "./table/TableOverlays";
import { actionAvailability } from "./actionAvailability";
import { readPlayerName } from "./namePreference";

export default function App() {
  const [tableFormat, setTableFormat] = useState<GameFormat>("COMMANDER");
  const [firstPlayer, setFirstPlayer] = useState(0);
  const [name, setName] = useState(readPlayerName);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [viewer, setViewer] = useState<{
    seat: number;
    zone: string;
    trigger: HTMLElement | null;
  } | null>(null);
  const onNavigate = useCallback(() => {
    setSelectedId(null);
    setViewer(null);
  }, []);
  const session = useTableSession(onNavigate, setFirstPlayer);
  const { view, socket, act, fail } = session;
  useEffect(() => {
    if (
      view &&
      selectedId &&
      !view.cards.some((card) => card.id === selectedId)
    )
      setSelectedId(null);
  }, [view, selectedId]);
  const { disabled, canPlay } = actionAvailability({
    connected: socket.connected,
    pending: socket.pending,
    busy: session.busy,
    view,
  });
  const inspect = useCallback((card: Card) => setSelectedId(card.id), []);
  const openZone = (seat: number, zone: string) => {
    const trigger =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    if (zone === "library") act("SEARCH", { target: seat });
    setViewer({ seat, zone, trigger });
  };
  return (
    <div className="app-shell">
      <TableHeader
        room={session.room}
        hasView={!!view}
        connected={socket.connected}
        fail={fail}
      />
      <ErrorNotice error={session.error} dismiss={() => fail("")} />
      <AppScreen
        view={view}
        room={session.room}
        csrf={session.csrf}
        busy={session.busy}
        saved={session.saved}
        name={name}
        setName={setName}
        tableFormat={tableFormat}
        setTableFormat={setTableFormat}
        firstPlayer={firstPlayer}
        setFirstPlayer={setFirstPlayer}
        disabled={disabled}
        canPlay={canPlay}
        act={act}
        preview={session.api.previewDeck}
        loadDraft={session.loadDraft}
        fail={fail}
        inspect={inspect}
        openZone={openZone}
        socketOnline={socket.online}
        onEnter={() => void session.enter(name, tableFormat)}
      />
      <FinishedNotice finished={view?.status === "finished"} />
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

type AppScreenProps = Omit<ComponentProps<typeof WelcomeScreen>, "enter"> &
  Omit<ComponentProps<typeof LobbyScreen>, "view"> &
  Omit<ComponentProps<typeof PlayingTable>, "view"> & {
    view: Snapshot | null;
    onEnter: () => void;
  };

function AppScreen(props: AppScreenProps) {
  if (!props.view) {
    return (
      <WelcomeScreen
        room={props.room}
        name={props.name}
        setName={props.setName}
        tableFormat={props.tableFormat}
        setTableFormat={props.setTableFormat}
        csrf={props.csrf}
        busy={props.busy}
        saved={props.saved}
        enter={(event) => {
          event.preventDefault();
          props.onEnter();
        }}
      />
    );
  }
  if (props.view.status === "lobby") {
    return (
      <LobbyScreen
        view={props.view}
        disabled={props.disabled}
        firstPlayer={props.firstPlayer}
        setFirstPlayer={props.setFirstPlayer}
        act={props.act}
        preview={props.preview}
        loadDraft={props.loadDraft}
        fail={props.fail}
      />
    );
  }
  if (props.view.status === "opening") {
    return (
      <OpeningHands
        view={props.view}
        disabled={props.disabled}
        act={props.act}
        inspect={props.inspect}
      />
    );
  }
  return (
    <PlayingTable
      view={props.view}
      canPlay={props.canPlay}
      disabled={props.disabled}
      act={props.act}
      inspect={props.inspect}
      openZone={props.openZone}
      socketOnline={props.socketOnline}
    />
  );
}

function ErrorNotice({
  error,
  dismiss,
}: {
  error: string;
  dismiss: () => void;
}) {
  return error ? (
    <div role="alert" className="error-banner">
      {error}
      <button aria-label="Dismiss error" onClick={dismiss}>
        ×
      </button>
    </div>
  ) : null;
}

function FinishedNotice({ finished }: { finished: boolean }) {
  return finished ? (
    <div className="finished-banner">
      Game finished. Your table has been saved.
    </div>
  ) : null;
}
