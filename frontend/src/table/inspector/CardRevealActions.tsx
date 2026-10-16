import type { Player } from "../../protocol";
import type { Action } from "../types";

export function TableRevealActions({
  players,
  you,
  send,
}: {
  players: Player[];
  you: number;
  send: Action;
}) {
  return (
    <>
      <button
        onClick={() =>
          send("REVEAL", {
            audience: players.filter((p) => p.seat !== you).map((p) => p.seat),
          })
        }
      >
        Reveal to table
      </button>
      <button onClick={() => send("REVEAL", { audience: [] })}>
        End reveal
      </button>
    </>
  );
}

export function CardRevealActions({
  players,
  target,
  setTarget,
  send,
}: {
  players: Player[];
  target: number;
  setTarget: (seat: number) => void;
  send: Action;
}) {
  return (
    <details>
      <summary>Reveal to one player</summary>
      <select
        aria-label="Reveal audience"
        value={target}
        onChange={(e) => setTarget(Number(e.target.value))}
      >
        {players.map((p) => (
          <option key={p.seat} value={p.seat}>
            {p.name}
          </option>
        ))}
      </select>
      <button onClick={() => send("REVEAL", { audience: [target] })}>
        Reveal to selected player
      </button>
    </details>
  );
}
