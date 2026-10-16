import { describe, expect, it, vi } from "vitest";
import {
  decodeDraft,
  decodeSavedRooms,
  decodeServerMessage,
  decodeSession,
  decodeSnapshot,
  ProtocolError,
} from "./protocol";
import { definitionFixture, snapshotFixture } from "./protocolFixtures";
import { applyServerMessage } from "./socketMessages";

describe("validated protocol", () => {
  it.each(["COMMANDER", "STANDARD"] as const)(
    "accepts a complete %s snapshot without changing hidden/null fields",
    (format) => {
      const value = snapshotFixture(format);
      const decoded = decodeSnapshot(value);
      expect(decoded).toEqual(value);
      expect(decoded.cards[1]).not.toHaveProperty("definition");
      expect(decoded.cards[0].commanderKey).toBeNull();
    },
  );

  it("accepts omitted optional fields, additional properties, and legacy legality metadata", () => {
    const value = snapshotFixture();
    delete value.cards[0].commanderKey;
    delete value.cards[0].attachedTo;
    delete value.cards[0].definition!.legalities;
    delete value.cards[0].definition!.legalityCheckedAt;
    expect(decodeSnapshot({ ...value, futureField: "ignored" })).toEqual(value);
  });

  const valid = snapshotFixture();
  it.each([
    null,
    [],
    {},
    { ...valid, format: "UNKNOWN" },
    { ...valid, revision: "7" },
    { ...valid, search: "0" },
    { ...valid, players: [{ ...valid.players[0], ready: "yes" }] },
    { ...valid, cards: [{ ...valid.cards[0], counters: { poison: "one" } }] },
    {
      ...valid,
      cards: [
        {
          ...valid.cards[0],
          definition: { ...definitionFixture(), faces: [{ name: 1 }] },
        },
      ],
    },
    { ...valid, cards: [{ ...valid.cards[0], x: Infinity }] },
    { ...valid, abilities: [{ id: "ability" }] },
    { ...valid, grants: ["0"] },
    { ...valid, log: [1] },
    { ...valid, seatCapacity: 2.5 },
  ])("rejects malformed snapshots before returning data %#", (value) => {
    expect(() => decodeSnapshot(value)).toThrow(ProtocolError);
  });

  it("validates session, saved rooms, and draft definitions", () => {
    expect(decodeSession({ csrf: "token" })).toEqual({ csrf: "token" });
    expect(
      decodeSavedRooms([{ id: "room", status: "lobby", format: "STANDARD" }]),
    ).toHaveLength(1);
    const draft = {
      draftId: "draft",
      choices: [definitionFixture()],
      suggested: ["definition"],
      count: 60,
    };
    expect(decodeDraft(draft)).toEqual(draft);
    for (const parse of [
      () => decodeSession({ csrf: 3 }),
      () => decodeSavedRooms([{ id: "room", format: "INVALID" }]),
      () => decodeDraft({ ...draft, choices: [null] }),
      () => decodeDraft({ ...draft, suggested: [1] }),
    ])
      expect(parse).toThrow(ProtocolError);
  });

  it.each([
    { type: "snapshot", view: valid },
    { type: "ack", commandId: "command", view: valid },
    { type: "presence", online: [] },
    { type: "error" },
    { type: "error", message: null, view: valid },
    { type: "error", message: "Denied" },
  ])("decodes every supported message shape %#", (value) => {
    expect(decodeServerMessage(JSON.stringify(value))).toEqual(value);
  });

  it.each([
    "not JSON",
    "null",
    "[]",
    JSON.stringify({ type: "future" }),
    JSON.stringify({ type: "snapshot" }),
    JSON.stringify({ type: "ack", commandId: "c" }),
    JSON.stringify({ type: "ack", view: valid }),
    JSON.stringify({ type: "presence", online: ["0"] }),
    JSON.stringify({ type: "error", view: { id: "room" } }),
    JSON.stringify({ type: "error", message: 1 }),
    new Blob(["text"]),
    new ArrayBuffer(1),
    7,
  ])("rejects invalid frames without effects %#", (value) => {
    const handlers = {
      receive: vi.fn(),
      presence: vi.fn(),
      acknowledge: vi.fn(),
      fail: vi.fn(),
    };
    expect(() => applyServerMessage(value, handlers)).toThrow(ProtocolError);
    for (const handler of Object.values(handlers))
      expect(handler).not.toHaveBeenCalled();
  });

  it("applies views before acknowledgments and errors, and supports presence", () => {
    const order: string[] = [];
    const handlers = {
      receive: () => order.push("view"),
      presence: () => order.push("presence"),
      acknowledge: () => order.push("ack"),
      fail: (message: string) => order.push(message),
    };
    applyServerMessage(
      JSON.stringify({ type: "ack", commandId: "c", view: valid }),
      handlers,
    );
    expect(order).toEqual(["view", "ack"]);
    order.length = 0;
    applyServerMessage(
      JSON.stringify({ type: "error", view: valid, message: null }),
      handlers,
    );
    expect(order).toEqual(["view", "ack", "Action failed"]);
    order.length = 0;
    applyServerMessage(
      JSON.stringify({ type: "presence", online: [] }),
      handlers,
    );
    expect(order).toEqual(["presence"]);
  });

  it("does not expose payload values in protocol diagnostics", () => {
    try {
      decodeSnapshot({ secret: "PRIVATE-CARD-SECRET" });
    } catch (error) {
      expect(error).toBeInstanceOf(ProtocolError);
      expect(String(error)).not.toContain("PRIVATE-CARD-SECRET");
      expect(error).not.toHaveProperty("cause");
    }
    expect(() => decodeServerMessage("PRIVATE-CARD-SECRET")).toThrow(
      "Could not read a server update. Refresh the table.",
    );
  });
});
