import { useState } from "react";
import type { Card, Snapshot } from "../protocol";
import type { Action, Inspect } from "./types";
import { Modal } from "./Modal";
import { CardDetails } from "./inspector/CardDetails";
import { CardQuickActions } from "./inspector/CardQuickActions";
import { CardMovementActions } from "./inspector/CardMovementActions";
import { CardCounterEditor } from "./inspector/CardCounterEditor";
import { PermanentRelationsEditor } from "./inspector/PermanentRelationsEditor";
import { CombatEditor } from "./inspector/CombatEditor";
import { CardRevealActions } from "./inspector/CardRevealActions";
import { EditTokenAction, CopyCardAction } from "./inspector/TokenActions";

export function Inspector({
  card,
  view,
  act,
  disabled,
  close,
  inspect,
}: {
  inspect: Inspect;
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
  const editable = card.editable && !disabled;
  const send: Action = (type, data = {}) =>
    act(type, { cardId: card.id, ...data });
  return (
    <Modal className="inspector" label="Card inspector" close={close}>
      <label>
        Inspect another card
        <select
          value={card.id}
          onChange={(event) => {
            const next = view.cards.find(
              (candidate) => candidate.id === event.target.value,
            );
            if (next) inspect(next);
          }}
        >
          {view.cards.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.definition?.faces[candidate.face]?.name ||
                candidate.definition?.name ||
                "Face-down card"}{" "}
              · {view.players[candidate.controller].name} · {candidate.zone}
            </option>
          ))}
        </select>
      </label>
      <CardDetails card={card} players={view.players} close={close} />
      <AttachmentSummary card={card} view={view} />
      {editable && (
        <>
          <CardQuickActions
            card={card}
            players={view.players}
            you={view.you}
            send={send}
          />
          <CardMovementActions card={card} send={send} />
          <CardCounterEditor
            counter={counter}
            setCounter={setCounter}
            send={send}
          />
          {card.zone === "battlefield" && (
            <>
              <PermanentRelationsEditor
                cardId={card.id}
                players={view.players}
                cards={view.cards}
                target={target}
                setTarget={setTarget}
                attachment={attachment}
                setAttachment={setAttachment}
                send={send}
              />
              <CombatEditor
                cardId={card.id}
                cards={view.cards}
                blocker={blocker}
                setBlocker={setBlocker}
                send={send}
              />
            </>
          )}
          <CardRevealActions
            players={view.players}
            target={target}
            setTarget={setTarget}
            send={send}
          />
          <EditTokenAction card={card} send={send} />
        </>
      )}
      <CopyCardAction card={card} disabled={disabled} send={send} />
    </Modal>
  );
}

function AttachmentSummary({ card, view }: { card: Card; view: Snapshot }) {
  if (!card.attachedTo) return null;
  const target = view.cards.find(
    (candidate) => candidate.id === card.attachedTo,
  );
  const name =
    target?.definition?.faces[target.face]?.name ||
    target?.definition?.name ||
    "Face-down card";
  return (
    <p className="attachment-summary">
      Attached to: {name}
      {target ? ` · ${view.players[target.controller].name}` : ""}
    </p>
  );
}
