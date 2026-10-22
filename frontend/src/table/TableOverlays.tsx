import { useEffect, useRef } from "react";
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
  viewer: { seat: number; zone: string; trigger: HTMLElement | null } | null;
  selected: Card | undefined;
  inspect: Inspect;
  act: Action;
  canPlay: boolean;
  closeViewer: () => void;
  closeInspector: () => void;
}) {
  const viewerTrigger = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (viewer) {
      viewerTrigger.current = viewer.trigger;
      return;
    }
    const trigger = viewerTrigger.current;
    if (!trigger || trigger.matches(":disabled")) return;
    viewerTrigger.current = null;
    // A pending CLOSE_SEARCH can disable the trigger until its acknowledgment.
    if (trigger.isConnected && document.activeElement === document.body)
      trigger.focus();
  }, [viewer, canPlay]);
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
          inspect={inspect}
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
