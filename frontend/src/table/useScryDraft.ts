import { useState } from "react";
import type { Card, LibraryInspection } from "../protocol";

type Draft = { top: string[]; bottom: string[] };
export function useScryDraft(inspection: LibraryInspection, visible: Card[]) {
  const [draft, setDraft] = useState<Draft>({
    top: [...inspection.cardIds],
    bottom: [],
  });
  const cards = visible.filter(
    (card) =>
      card.zone === "library" &&
      card.zoneOwner === inspection.target &&
      inspection.cardIds.includes(card.id),
  );
  const partition = [...draft.top, ...draft.bottom];
  const valid =
    cards.length === inspection.cardIds.length &&
    partition.length === cards.length &&
    new Set(partition).size === partition.length &&
    partition.every((id) => inspection.cardIds.includes(id));
  const move = (id: string, group: keyof Draft) =>
    setDraft((current) => ({
      top: [
        ...current.top.filter((value) => value !== id),
        ...(group === "top" ? [id] : []),
      ],
      bottom: [
        ...current.bottom.filter((value) => value !== id),
        ...(group === "bottom" ? [id] : []),
      ],
    }));
  const reorder = (group: keyof Draft, index: number, delta: number) =>
    setDraft((current) => {
      const ids = [...current[group]];
      [ids[index], ids[index + delta]] = [ids[index + delta], ids[index]];
      return { ...current, [group]: ids };
    });
  return { draft, cards, valid, move, reorder };
}
