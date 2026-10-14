import type { Card } from "./protocol";

const groups = [
  { type: "creature", label: "Creatures" },
  { type: "planeswalker", label: "Planeswalkers" },
  { type: "battle", label: "Battles" },
  { type: "artifact", label: "Artifacts" },
  { type: "enchantment", label: "Enchantments" },
  { type: "other", label: "Other" },
  { type: "land", label: "Lands" },
] as const;

type PermanentType = (typeof groups)[number]["type"];
export interface BattlefieldGroup {
  type: PermanentType;
  label: string;
  cards: Card[];
}

function primaryType(card: Card): PermanentType {
  if (card.faceDown || !card.definition) return "other";
  const type = card.definition.faces[card.face]?.type || card.definition.type;
  const words = new Set(
    type
      .split(/[—–]/, 1)[0]
      .toLowerCase()
      .match(/[a-z]+/g),
  );
  if (words.has("land")) return "land";
  return groups.find((group) => words.has(group.type))?.type || "other";
}

export function groupBattlefield(cards: readonly Card[]): BattlefieldGroup[] {
  const byType = new Map<PermanentType, Card[]>();
  for (const card of cards) {
    const type = primaryType(card);
    const group = byType.get(type);
    if (group) group.push(card);
    else byType.set(type, [card]);
  }
  return groups.flatMap((group) => {
    const cards = byType.get(group.type);
    return cards ? [{ ...group, cards }] : [];
  });
}
