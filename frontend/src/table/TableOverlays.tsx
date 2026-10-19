import type { Card, Snapshot } from "../protocol";
import type { Inspect, Action } from "./types";
import { ZoneViewer } from "./ZoneViewer";
import { Inspector } from "./Inspector";

export function TableOverlays({
  view,
  viewer,
  selected,
  inspect,
  act,
  canPlay,
  closeViewer,
  closeInspector,
}: {
  view: Snapshot;
  viewer: { seat: number; zone: string } | null;
  selected: Card | undefined;
  inspect: Inspect;
  act: Action;
  canPlay: boolean;
  closeViewer: () => void;
  closeInspector: () => void;
}) {
  return (
    <>
      {viewer && (
        <ZoneViewer
          key={`${view.id}-${viewer.seat}-${viewer.zone}`}
          act={act}
          disabled={!canPlay}
          cards={view.cards}
          seat={viewer.seat}
          zone={viewer.zone}
          playerName={view.players[viewer.seat].name}
          inspect={inspect}
          close={closeViewer}
        />
      )}
      {selected && (
        <Inspector
          card={selected}
          view={view}
          act={act}
          disabled={!canPlay}
          close={closeInspector}
        />
      )}
    </>
  );
}
