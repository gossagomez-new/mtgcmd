import { z } from "zod";

const integer = z.number().int();
const seat = integer.nonnegative();
const counts = z.record(z.string(), integer.nonnegative());
const counters = z.record(z.string(), integer);
const formatSchema = z.enum(["COMMANDER", "STANDARD"]);
const definitionSchema = z.object({
  id: z.string(),
  name: z.string(),
  image: z.string(),
  text: z.string(),
  type: z.string(),
  colors: z.array(z.string()),
  legal: z.boolean(),
  legalities: z.record(z.string(), z.string()).optional(),
  legalityCheckedAt: integer.nullish(),
  faces: z.array(
    z.object({
      name: z.string(),
      image: z.string(),
      text: z.string(),
      type: z.string(),
    }),
  ),
});
const cardSchema = z.object({
  id: z.string(),
  definition: definitionSchema.optional(),
  owner: seat,
  controller: seat,
  zoneOwner: seat,
  zone: z.string(),
  tapped: z.boolean(),
  faceDown: z.boolean(),
  face: integer.nonnegative(),
  token: z.boolean(),
  commanderKey: z.string().nullish(),
  attachedTo: z.string().nullish(),
  combat: z.string(),
  x: z.number(),
  y: z.number(),
  counters,
  editable: z.boolean(),
  stackOrder: integer,
});
const playerSchema = z.object({
  seat,
  name: z.string(),
  ready: z.boolean(),
  deckRevision: integer.nonnegative(),
  privateAccess: z.boolean(),
  deckCount: integer.nonnegative(),
  warnings: z.array(z.string()),
  conceded: z.boolean(),
  counters,
  counts,
  commanders: z.array(
    z.object({ key: z.string(), name: z.string(), casts: integer }),
  ),
});
const snapshotSchema = z.object({
  format: formatSchema,
  seatCapacity: integer.positive(),
  id: z.string(),
  revision: integer.nonnegative(),
  status: z.string(),
  host: seat,
  active: seat,
  turn: integer,
  phase: z.string(),
  you: seat,
  players: z.array(playerSchema),
  cards: z.array(cardSchema),
  abilities: z.array(
    z.object({
      id: z.string(),
      controller: seat,
      text: z.string(),
      stackOrder: integer,
    }),
  ),
  log: z.array(z.string()),
  grants: z.array(seat),
  search: seat.nullable(),
});
const sessionSchema = z.object({ csrf: z.string() });
const savedRoomsSchema = z.array(
  z.object({ id: z.string(), status: z.string(), format: formatSchema }),
);
const draftSchema = z.object({
  draftId: z.string(),
  choices: z.array(definitionSchema),
  suggested: z.array(z.string()),
  count: integer.nonnegative(),
});
const messageSchema = z.discriminatedUnion("type", [
  z.object({ type: z.literal("snapshot"), view: snapshotSchema }),
  z.object({
    type: z.literal("ack"),
    commandId: z.string(),
    view: snapshotSchema,
  }),
  z.object({
    type: z.literal("error"),
    message: z.string().nullish(),
    view: snapshotSchema.optional(),
  }),
  z.object({ type: z.literal("presence"), online: z.array(seat) }),
]);

export type GameFormat = z.infer<typeof formatSchema>;
export type CardDefinition = z.infer<typeof definitionSchema>;
export type Card = z.infer<typeof cardSchema>;
export type Player = z.infer<typeof playerSchema>;
export type Snapshot = z.infer<typeof snapshotSchema>;
export type Draft = z.infer<typeof draftSchema>;
export type SavedRoom = z.infer<typeof savedRoomsSchema>[number];
export type Message = z.infer<typeof messageSchema>;

export class ProtocolError extends Error {
  override name = "ProtocolError";
}

function decode<T>(
  schema: z.ZodType<T>,
  value: unknown,
  message = "Unexpected server response. Refresh before retrying.",
): T {
  const result = schema.safeParse(value);
  // Never attach raw schema issues: validation diagnostics can include private input.
  if (!result.success) throw new ProtocolError(message);
  return result.data;
}

export const decodeSnapshot = (value: unknown): Snapshot =>
  decode(snapshotSchema, value);
export const decodeSession = (value: unknown) => decode(sessionSchema, value);
export const decodeSavedRooms = (value: unknown): SavedRoom[] =>
  decode(savedRoomsSchema, value);
export const decodeDraft = (value: unknown): Draft =>
  decode(draftSchema, value);

export function decodeServerMessage(data: unknown): Message {
  const message = "Could not read a server update. Refresh the table.";
  if (typeof data !== "string") throw new ProtocolError(message);
  let value: unknown;
  try {
    value = JSON.parse(data);
  } catch {
    throw new ProtocolError(message);
  }
  return decode(messageSchema, value, message);
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
