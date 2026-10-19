import type { Card, Player } from "../../protocol";
import type { Action } from "../types";

export function PermanentRelationsEditor({
  cardId,
  players,
  cards,
  target,
  setTarget,
  attachment,
  setAttachment,
  send,
}: {
  cardId: string;
  players: Player[];
  cards: Card[];
  target: number;
  setTarget: (seat: number) => void;
  attachment: string;
  setAttachment: (id: string) => void;
  send: Action;
}) {
  return (
    <>
      <label>
        Transfer control
        <select
          value={target}
          onChange={(e) => setTarget(Number(e.target.value))}
        >
          {players.map((p) => (
            <option key={p.seat} value={p.seat}>
              {p.name}
            </option>
          ))}
        </select>
      </label>
      <button onClick={() => send("CONTROL", { target })}>Give control</button>
      <label>
        Attach to
        <select
          value={attachment}
          onChange={(e) => setAttachment(e.target.value)}
        >
          <option value="">Choose a permanent</option>
          {cards
            .filter((c) => c.zone === "battlefield" && c.id !== cardId)
            .map((c) => (
              <option key={c.id} value={c.id}>
                {c.definition?.name || "Face-down card"} ·{" "}
                {players[c.controller].name}
              </option>
            ))}
        </select>
      </label>
      <button
        disabled={
          !cards.some(
            (card) =>
              card.id === attachment &&
              card.zone === "battlefield" &&
              card.id !== cardId,
          )
        }
        onClick={() => send("ATTACH", { targetId: attachment })}
      >
        Attach
      </button>
      <button
        disabled={!cards.find((card) => card.id === cardId)?.attachedTo}
        onClick={() => send("ATTACH", { targetId: "" })}
      >
        Detach
      </button>
    </>
  );
}
