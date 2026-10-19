import type { Card } from "../protocol";
import type { Action, Inspect } from "./types";
import { CardActionPopover } from "./CardActionPopover";
import { CardPlacementButtons } from "./inspector/CardMovementActions";
import { TapCardButton } from "./inspector/CardQuickActions";

export function CardActionMenu({
  card,
  anchor,
  disabled,
  act,
  inspect,
  close,
}: {
  anchor: HTMLElement;
  card: Card;
  disabled: boolean;
  act: Action;
  inspect: Inspect;
  close: () => void;
}) {
  const dismiss = () => {
    if (anchor.isConnected) anchor.focus();
    close();
  };
  const send: Action = (type, data = {}) => {
    if (disabled || !card.editable) return;
    act(type, { cardId: card.id, ...data });
    dismiss();
  };
  return (
    <CardActionPopover anchor={anchor} close={close}>
      {card.editable && !disabled && (
        <>
          {card.zone === "battlefield" && (
            <TapCardButton card={card} send={send} menu />
          )}
          <CardPlacementButtons card={card} send={send} menu />
        </>
      )}
      <button role="menuitem" onClick={dismiss}>
        Close menu
      </button>
      <button
        role="menuitem"
        onClick={() => {
          dismiss();
          inspect(card);
        }}
      >
        Details
      </button>
    </CardActionPopover>
  );
}
