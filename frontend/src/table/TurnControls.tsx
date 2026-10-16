import type { Snapshot } from "../protocol";
import type { Action } from "./types";

export function TurnControls({
  view,
  canPlay,
  act,
}: {
  view: Snapshot;
  canPlay: boolean;
  act: Action;
}) {
  return (
    <div className="turn-strip">
      <div>
        <span className="eyebrow">TURN {view.turn}</span>
        <strong>
          {view.players[view.active].name}
          <span className="subtle"> / {view.phase}</span>
        </strong>
      </div>
      <button
        disabled={
          !canPlay || (view.you !== view.active && view.you !== view.host)
        }
        onClick={() => act("PHASE")}
      >
        Next phase →
      </button>
      <span className="phase-reminder">
        Players manage priority and triggers
      </span>
    </div>
  );
}
