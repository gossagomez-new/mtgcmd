import type { Card } from "../../protocol";
import type { Action } from "../types";

export function CombatEditor({
  cardId,
  cards,
  blocker,
  setBlocker,
  send,
}: {
  cardId: string;
  cards: Card[];
  blocker: string;
  setBlocker: (id: string) => void;
  send: Action;
}) {
  return (
    <>
      <div className="action-grid">
        <button onClick={() => send("COMBAT", { marker: "attacker" })}>
          Mark attacker
        </button>
        <button onClick={() => send("COMBAT", { marker: "" })}>
          Clear combat
        </button>
      </div>
      <label>
        Block attacker
        <select value={blocker} onChange={(e) => setBlocker(e.target.value)}>
          <option value="">Choose attacker</option>
          {cards
            .filter(
              (c) =>
                c.zone === "battlefield" &&
                c.combat === "attacker" &&
                c.id !== cardId,
            )
            .map((c) => (
              <option key={c.id} value={c.id}>
                {c.definition?.name || "Face-down card"}
              </option>
            ))}
        </select>
      </label>
      <button
        disabled={!blocker}
        onClick={() => send("COMBAT", { marker: `blocks:${blocker}` })}
      >
        Mark blocker
      </button>
    </>
  );
}
