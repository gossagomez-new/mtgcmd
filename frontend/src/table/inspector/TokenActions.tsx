import type { Card } from "../../protocol";
import type { Action } from "../types";

export function EditTokenAction({ card, send }: { card: Card; send: Action }) {
  return (
    <>
      {card.token && (
        <button
          onClick={() => {
            const name = prompt("Token name", card.definition?.name);
            if (!name) return;
            const type = prompt("Token type", card.definition?.type) || "";
            const text =
              prompt("Power/toughness and text", card.definition?.text) || "";
            send("EDIT_TOKEN", { name, type, text });
          }}
        >
          Edit token characteristics
        </button>
      )}
    </>
  );
}

export function CopyCardAction({
  card,
  disabled,
  send,
}: {
  card: Card;
  disabled: boolean;
  send: Action;
}) {
  return (
    <>
      {card.definition &&
        !disabled &&
        !["library", "hand"].includes(card.zone) && (
          <button onClick={() => send("COPY")}>Create token copy</button>
        )}
    </>
  );
}
