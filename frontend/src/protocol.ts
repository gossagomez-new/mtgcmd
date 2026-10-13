export interface CardDefinition {
  id: string;
  name: string;
  image: string;
  text: string;
  type: string;
  colors: string[];
  legal: boolean;
  faces: { name: string; image: string; text: string; type: string }[];
}
export interface Card {
  id: string;
  definition?: CardDefinition;
  owner: number;
  controller: number;
  zoneOwner: number;
  zone: string;
  tapped: boolean;
  faceDown: boolean;
  face: number;
  token: boolean;
  commanderKey?: string;
  attachedTo?: string;
  combat: string;
  x: number;
  y: number;
  counters: Record<string, number>;
  editable: boolean;
  stackOrder: number;
}
export interface Player {
  seat: number;
  name: string;
  ready: boolean;
  deckRevision: number;
  privateAccess: boolean;
  deckCount: number;
  warnings: string[];
  conceded: boolean;
  counters: Record<string, number>;
  counts: Record<string, number>;
  commanders: { key: string; name: string; casts: number }[];
}
export interface Snapshot {
  id: string;
  revision: number;
  status: string;
  host: number;
  active: number;
  turn: number;
  phase: string;
  you: number;
  players: Player[];
  cards: Card[];
  abilities: {
    id: string;
    controller: number;
    text: string;
    stackOrder: number;
  }[];
  log: string[];
  grants: number[];
  search: number | null;
}
export interface Message {
  type: string;
  view?: Snapshot;
  message?: string;
  commandId?: string;
  online?: number[];
}
export function acceptSnapshot(
  current: Snapshot | null,
  incoming: Snapshot,
): Snapshot {
  return current &&
    current.id === incoming.id &&
    current.revision > incoming.revision
    ? current
    : incoming;
}
export function commandPayload(
  revision: number,
  type: string,
  data: Record<string, unknown>,
) {
  return { id: crypto.randomUUID(), revision, type, data };
}
