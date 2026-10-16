import { useState } from "react";
import type { Card, Snapshot } from "../protocol";
import type { Action } from "./types";
const zones = [
  "library",
  "hand",
  "battlefield",
  "graveyard",
  "exile",
  "command",
  "stack",
];

export function Inspector({
  card,
  view,
  act,
  disabled,
  close,
}: {
  card: Card;
  view: Snapshot;
  act: Action;
  disabled: boolean;
  close: () => void;
}) {
  const [counter, setCounter] = useState("+1/+1");
  const [target, setTarget] = useState(view.you);
  const [attachment, setAttachment] = useState("");
  const [blocker, setBlocker] = useState("");
  const face = card.definition?.faces[card.face];
  const image = face?.image || card.definition?.image;
  const name = face?.name || card.definition?.name || "Face-down card";
  const editable = card.editable && !disabled;
  const action = (type: string, data: Record<string, unknown> = {}) =>
    act(type, { cardId: card.id, ...data });
  return (
    <aside
      className="inspector panel"
      role="dialog"
      aria-label="Card inspector"
    >
      <div className="zone-heading">
        <h2>{name}</h2>
        <button aria-label="Close inspector" onClick={close}>
          ×
        </button>
      </div>
      {image && <img className="inspect-image" src={image} alt={name} />}
      <p className="card-type">{face?.type || card.definition?.type}</p>
      <p className="rules-text">
        {face?.text ||
          card.definition?.text ||
          "This card’s identity is hidden."}
      </p>
      <small>
        Owner: {view.players[card.owner].name} · Controller:{" "}
        {view.players[card.controller].name}
      </small>
      <p className="eyebrow">
        {card.zone}
        {card.tapped ? " · TAPPED" : ""}
      </p>
      {editable && (
        <>
          <div className="action-grid">
            <button
              onClick={() => action("MOVE", { zone: "exile", faceDown: true })}
            >
              Exile face down
            </button>
            {card.zone !== "battlefield" && (
              <>
                <button
                  className="primary"
                  onClick={() => action("MOVE", { zone: "battlefield" })}
                >
                  Play to battlefield
                </button>
                <button
                  onClick={() =>
                    action("MOVE", { zone: "battlefield", faceDown: true })
                  }
                >
                  Play face down
                </button>
              </>
            )}
            {card.zone === "battlefield" && (
              <>
                <button onClick={() => action("TAP")}>
                  {card.tapped ? "Untap" : "Tap"}
                </button>
                <button onClick={() => action("FACE_DOWN")}>
                  {card.faceDown ? "Turn face up" : "Turn face down"}
                </button>
              </>
            )}
            <button
              onClick={() =>
                action("REVEAL", {
                  audience: view.players
                    .filter((p) => p.seat !== view.you)
                    .map((p) => p.seat),
                })
              }
            >
              Reveal to table
            </button>
            <button onClick={() => action("REVEAL", { audience: [] })}>
              End reveal
            </button>
            {(card.definition?.faces.length || 0) > 1 && (
              <button onClick={() => action("TRANSFORM")}>Other face</button>
            )}
          </div>
          <label>
            Move to zone
            <select
              value=""
              onChange={(e) => {
                if (e.target.value) action("MOVE", { zone: e.target.value });
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
            <button
              onClick={() => action("MOVE", { zone: "library", top: true })}
            >
              Library top
            </button>
            <button onClick={() => action("MOVE", { zone: "library" })}>
              Library bottom
            </button>
          </div>
          <label>
            Card counter
            <input
              value={counter}
              onChange={(e) => setCounter(e.target.value)}
              maxLength={100}
            />
          </label>
          <div className="action-grid">
            <button
              disabled={!counter.trim()}
              onClick={() =>
                action("CARD_COUNTER", { name: counter, delta: 1 })
              }
            >
              + Counter
            </button>
            <button
              disabled={!counter.trim()}
              onClick={() =>
                action("CARD_COUNTER", { name: counter, delta: -1 })
              }
            >
              − Counter
            </button>
          </div>
          {card.zone === "battlefield" && (
            <>
              <label>
                Transfer control
                <select
                  value={target}
                  onChange={(e) => setTarget(Number(e.target.value))}
                >
                  {view.players.map((p) => (
                    <option key={p.seat} value={p.seat}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </label>
              <button onClick={() => action("CONTROL", { target })}>
                Give control
              </button>
              <label>
                Attach to
                <select
                  value={attachment}
                  onChange={(e) => setAttachment(e.target.value)}
                >
                  <option value="">None / detach</option>
                  {view.cards
                    .filter((c) => c.zone === "battlefield" && c.id !== card.id)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.definition?.name || "Face-down card"} ·{" "}
                        {view.players[c.controller].name}
                      </option>
                    ))}
                </select>
              </label>
              <button
                onClick={() => action("ATTACH", { targetId: attachment })}
              >
                Set attachment
              </button>
              <div className="action-grid">
                <button
                  onClick={() => action("COMBAT", { marker: "attacker" })}
                >
                  Mark attacker
                </button>
                <button onClick={() => action("COMBAT", { marker: "" })}>
                  Clear combat
                </button>
              </div>
              <label>
                Block attacker
                <select
                  value={blocker}
                  onChange={(e) => setBlocker(e.target.value)}
                >
                  <option value="">Choose attacker</option>
                  {view.cards
                    .filter(
                      (c) =>
                        c.zone === "battlefield" &&
                        c.combat === "attacker" &&
                        c.id !== card.id,
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
                onClick={() =>
                  action("COMBAT", { marker: `blocks:${blocker}` })
                }
              >
                Mark blocker
              </button>
            </>
          )}
          <details>
            <summary>Reveal to one player</summary>
            <select
              aria-label="Reveal audience"
              value={target}
              onChange={(e) => setTarget(Number(e.target.value))}
            >
              {view.players.map((p) => (
                <option key={p.seat} value={p.seat}>
                  {p.name}
                </option>
              ))}
            </select>
            <button onClick={() => action("REVEAL", { audience: [target] })}>
              Reveal to selected player
            </button>
          </details>
          {card.token && (
            <button
              onClick={() => {
                const name = prompt("Token name", card.definition?.name);
                if (!name) return;
                const type = prompt("Token type", card.definition?.type) || "";
                const text =
                  prompt("Power/toughness and text", card.definition?.text) ||
                  "";
                action("EDIT_TOKEN", { name, type, text });
              }}
            >
              Edit token characteristics
            </button>
          )}
        </>
      )}
      {card.definition &&
        !disabled &&
        !["library", "hand"].includes(card.zone) && (
          <button onClick={() => action("COPY")}>Create token copy</button>
        )}
    </aside>
  );
}
