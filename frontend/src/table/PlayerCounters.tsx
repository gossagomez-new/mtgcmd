import { useState } from "react";
import type { Player } from "../protocol";
import type { Action } from "./types";

export function PlayerCounters({
  usesCommanders,
  player,
  players,
  act,
  disabled,
}: {
  usesCommanders: boolean;
  player: Player;
  players: Player[];
  act: Action;
  disabled: boolean;
}) {
  const [name, setName] = useState("");
  const rows = Object.entries(player.counters).filter(
    ([n]) =>
      n !== "life" && !n.startsWith("casts:") && !n.startsWith("damage:"),
  );
  return (
    <>
      <div className="counter-rows">
        {rows.map(([n, v]) => (
          <CounterRow
            key={n}
            label={n}
            value={v}
            disabled={disabled}
            change={(delta) => act("COUNTER", { name: n, delta })}
          />
        ))}
      </div>
      <form
        className="inline-form"
        onSubmit={(e) => {
          e.preventDefault();
          if (name.trim()) {
            act("COUNTER", { name: name.trim(), delta: 1 });
            setName("");
          }
        }}
      >
        <input
          aria-label="New player counter"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={100}
          placeholder="Energy, experience…"
        />
        <button disabled={disabled || !name.trim()}>+</button>
      </form>
      {usesCommanders && (
        <>
          <details>
            <summary>Commander damage received</summary>
            {players
              .flatMap((p) => p.commanders)
              .map((c) => (
                <CounterRow
                  key={c.key}
                  label={c.name}
                  value={player.counters[`damage:${c.key}`] || 0}
                  disabled={disabled}
                  change={(delta) =>
                    act("COUNTER", { name: `damage:${c.key}`, delta })
                  }
                />
              ))}
          </details>
          <details>
            <summary>Your commander casts</summary>
            {player.commanders.map((c) => (
              <div key={c.key}>
                <CounterRow
                  label={c.name}
                  value={c.casts}
                  disabled={disabled}
                  change={(delta) =>
                    act("COMMANDER_CAST", { key: c.key, delta })
                  }
                />
                <small>
                  Additional commander tax: {Math.max(0, c.casts) * 2}
                </small>
              </div>
            ))}
          </details>
        </>
      )}
    </>
  );
}
function CounterRow({
  label,
  value,
  change,
  disabled,
}: {
  label: string;
  value: number;
  change: (delta: number) => void;
  disabled: boolean;
}) {
  return (
    <div className="counter-row">
      <span>{label}</span>
      <button
        aria-label={`Decrease ${label}`}
        disabled={disabled}
        onClick={() => change(-1)}
      >
        −
      </button>
      <strong>{value}</strong>
      <button
        aria-label={`Increase ${label}`}
        disabled={disabled}
        onClick={() => change(1)}
      >
        +
      </button>
    </div>
  );
}
