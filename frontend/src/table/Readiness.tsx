import { useState } from "react";
import type { Player } from "../protocol";
import type { Action } from "./types";

export function Readiness({
  player,
  disabled,
  act,
}: {
  player: Player;
  disabled: boolean;
  act: Action;
}) {
  const [accepted, setAccepted] = useState(false);
  if (!player.deckCount) return null;
  return (
    <div className="readiness">
      <h3>{player.deckCount} cards loaded</h3>
      {player.warnings.length > 0 && (
        <>
          <ul className="warnings">
            {player.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
          <label className="check">
            <input
              type="checkbox"
              checked={accepted}
              onChange={(e) => setAccepted(e.target.checked)}
            />
            Accept deck warnings
          </label>
        </>
      )}
      {player.ready ? (
        <>
          <p className="ready-message">You are ready</p>
          <button
            disabled={disabled}
            onClick={() => act("READY", { ready: false })}
          >
            Change readiness
          </button>
        </>
      ) : (
        <button
          className="primary"
          disabled={disabled || (!!player.warnings.length && !accepted)}
          onClick={() =>
            act("READY", { ready: true, acceptWarnings: accepted })
          }
        >
          Ready to play
        </button>
      )}
    </div>
  );
}
