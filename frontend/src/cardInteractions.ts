import type { Card } from "./protocol";

const destinations = new Set([
  "battlefield",
  "hand",
  "library",
  "graveyard",
  "exile",
  "stack",
  "command",
]);

export function dropCommand(
  cards: readonly Card[],
  cardId: string,
  zone: string,
) {
  const card = cards.find((candidate) => candidate.id === cardId);
  if (!card?.editable || !destinations.has(zone) || card.zone === zone)
    return null;
  if (zone === "command" && !card.commanderKey) return null;
  return { cardId: card.id, zone };
}
