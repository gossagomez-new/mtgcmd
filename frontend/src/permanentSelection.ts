import type { Card } from "./protocol";

export function selectionState(
  ids: string[],
  cards: Card[],
  disabled: boolean,
) {
  return {
    count: ids.length,
    valid:
      !disabled &&
      ids.length > 0 &&
      ids.length <= 500 &&
      new Set(ids).size === ids.length &&
      ids.every((id) =>
        cards.some(
          (card) =>
            card.id === id && card.zone === "battlefield" && card.editable,
        ),
      ),
  };
}
