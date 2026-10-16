import type { Card, Player } from "../../protocol";
import type { Action } from "../types";
import { CardPlacementButtons } from "./CardMovementActions";
import { TableRevealActions } from "./CardRevealActions";

export function CardQuickActions({
  card,
  players,
  you,
  send,
}: {
  card: Card;
  players: Player[];
  you: number;
  send: Action;
}) {
  return (
    <div className="action-grid">
      <CardPlacementButtons card={card} send={send} />
      <CardIdentityActions card={card} send={send} />
      <TableRevealActions players={players} you={you} send={send} />
      {(card.definition?.faces.length || 0) > 1 && (
        <button onClick={() => send("TRANSFORM")}>Other face</button>
      )}
    </div>
  );
}

function CardIdentityActions({ card, send }: { card: Card; send: Action }) {
  return (
    <>
      {card.zone === "battlefield" && (
        <>
          <button onClick={() => send("TAP")}>
            {card.tapped ? "Untap" : "Tap"}
          </button>
          <button onClick={() => send("FACE_DOWN")}>
            {card.faceDown ? "Turn face up" : "Turn face down"}
          </button>
        </>
      )}
    </>
  );
}
