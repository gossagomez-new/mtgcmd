import { useState } from "react";
import { Modal } from "./Modal";
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
  const [focused, setFocused] = useState<number | null>(null);
  const player = view.players.find((player) => player.seat === focused);
  const usesCommanders = view.format !== "STANDARD";
  return (
    <div className={`opponents ${usesCommanders ? "" : "duel-opponents"}`}>
      {view.players
        .filter((p) => p.seat !== view.you)
        .map((p) => (
          <PlayerArea
            key={p.seat}
            focus={() => setFocused(p.seat)}
            player={p}
            view={view}
            online={online.includes(p.seat)}
            inspect={inspect}
            openZone={openZone}
            act={act}
            disabled={!canPlay}
          />
        ))}
      {player && (
        <Modal
          label={`${player.name} battlefield`}
          className="focused-battlefield"
          close={() => setFocused(null)}
        >
          <button onClick={() => setFocused(null)}>
            Close focused battlefield
          </button>
          <PlayerArea
            player={player}
            view={view}
            online={online.includes(player.seat)}
            inspect={inspect}
            openZone={(seat, zone) => {
              setFocused(null);
              openZone(seat, zone);
            }}
            act={act}
            disabled={!canPlay}
          />
        </Modal>
      )}
    </div>
  );
}
