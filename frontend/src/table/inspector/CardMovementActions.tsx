import type { Card } from "../../protocol";
import type { Action } from "../types";

const zones = [
  "library",
  "hand",
  "battlefield",
  "graveyard",
  "exile",
  "command",
  "stack",
];

export function CardPlacementButtons({
  card,
  send,
  menu = false,
}: {
  card: Card;
  send: Action;
  menu?: boolean;
}) {
  return (
    <>
      <button
        role={menu ? "menuitem" : undefined}
        onClick={() => send("MOVE", { zone: "exile", faceDown: true })}
      >
        Exile face down
      </button>
      {card.zone !== "battlefield" && (
        <>
          <button
            role={menu ? "menuitem" : undefined}
            className="primary"
            onClick={() => send("MOVE", { zone: "battlefield" })}
          >
            Play to battlefield
          </button>
          <button
            role={menu ? "menuitem" : undefined}
            onClick={() =>
              send("MOVE", { zone: "battlefield", faceDown: true })
            }
          >
            Play face down
          </button>
        </>
      )}
    </>
  );
}

export function CardMovementActions({
  card,
  send,
}: {
  card: Card;
  send: Action;
}) {
  return (
    <>
      <label>
        Move to zone
        <select
          value=""
          onChange={(e) => {
            if (e.target.value) send("MOVE", { zone: e.target.value });
          }}
        >
          <option value="">Choose destination</option>
          {zones
            .filter((z) => z !== "command" || card.commanderKey)
            .map((z) => (
              <option key={z} value={z}>
                {z}
              </option>
            ))}
        </select>
      </label>
      <div className="action-grid">
        <button onClick={() => send("MOVE", { zone: "library", top: true })}>
          Library top
        </button>
        <button onClick={() => send("MOVE", { zone: "library" })}>
          Library bottom
        </button>
      </div>
    </>
  );
}
