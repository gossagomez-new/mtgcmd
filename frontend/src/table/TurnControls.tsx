import { TurnNotification } from "./TurnNotification";
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
    <div className="turn-strip" role="region" aria-label="Turn controls">
      <div>
        <span className="eyebrow">TURN {view.turn}</span>
        <strong>
          {view.players[view.active].name}
          <span className="subtle"> / {view.phase}</span>
        </strong>
      </div>
      <TurnNotification view={view} />
      <div className="turn-actions">
        <button
          disabled={
            !canPlay || view.status !== "playing" || view.you !== view.active
          }
          onClick={() => act("PHASE")}
        >
          Next phase →
        </button>
        <button
          disabled={
            !canPlay || view.status !== "playing" || view.you !== view.active
          }
          onClick={() => act("END_TURN")}
        >
          End turn
        </button>
        <button
          disabled={
            !canPlay ||
            view.status !== "playing" ||
            view.you !== view.active ||
            !view.canGiveBackTurn
          }
          onClick={() => act("GIVE_BACK_TURN")}
        >
          Give back turn
        </button>
        <button disabled={!canPlay} onClick={() => act("UNTAP_ALL")}>
          Untap all
        </button>
      </div>
      <span className="phase-reminder">
        Players manage priority and triggers
      </span>
    </div>
  );
}
