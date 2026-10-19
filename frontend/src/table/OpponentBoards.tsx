import type { Snapshot } from "../protocol";
import type { Action, Inspect, OpenZone } from "./types";
import { PlayerArea } from "./CardViews";

export function OpponentBoards({
  view,
  online,
  inspect,
  openZone,
  act,
  canPlay,
}: {
  act: Action;
  canPlay: boolean;
  view: Snapshot;
  online: number[];
  inspect: Inspect;
  openZone: OpenZone;
}) {
  const usesCommanders = view.format !== "STANDARD";
  return (
    <div className={`opponents ${usesCommanders ? "" : "duel-opponents"}`}>
      {view.players
        .filter((p) => p.seat !== view.you)
        .map((p) => (
          <PlayerArea
            key={p.seat}
            player={p}
            view={view}
            online={online.includes(p.seat)}
            inspect={inspect}
            openZone={openZone}
            act={act}
            disabled={!canPlay}
          />
        ))}
    </div>
  );
}
