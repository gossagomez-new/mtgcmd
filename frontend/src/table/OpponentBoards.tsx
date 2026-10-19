import { useState } from "react";
import { FocusedBattlefieldPopover } from "./FocusedBattlefieldPopover";
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
  const [focused, setFocused] = useState<{
    seat: number;
    anchor: HTMLButtonElement;
  } | null>(null);
  const player = view.players.find((player) => player.seat === focused?.seat);
  const usesCommanders = view.format !== "STANDARD";
  return (
    <div className={`opponents ${usesCommanders ? "" : "duel-opponents"}`}>
      {view.players
        .filter((p) => p.seat !== view.you)
        .map((p) => (
          <PlayerArea
            key={p.seat}
            focus={(anchor) => setFocused({ seat: p.seat, anchor })}
            isFocused={focused?.seat === p.seat}
            player={p}
            view={view}
            online={online.includes(p.seat)}
            inspect={inspect}
            openZone={openZone}
            act={act}
            disabled={!canPlay}
          />
        ))}
      {player && focused && (
        <FocusedBattlefieldPopover
          key={player.seat}
          anchor={focused.anchor}
          id={`focused-battlefield-${player.seat}`}
          label={`${player.name} battlefield`}
          close={() =>
            setFocused((current) =>
              current?.seat === player.seat ? null : current,
            )
          }
        >
          <PlayerArea
            player={player}
            view={view}
            online={online.includes(player.seat)}
            inspect={(card) => {
              if (focused.anchor.isConnected)
                focused.anchor.focus({ preventScroll: true });
              setFocused(null);
              inspect(card);
            }}
            openZone={(seat, zone) => {
              if (focused.anchor.isConnected)
                focused.anchor.focus({ preventScroll: true });
              setFocused(null);
              openZone(seat, zone);
            }}
            act={act}
            disabled={!canPlay}
          />
        </FocusedBattlefieldPopover>
      )}
    </div>
  );
}
