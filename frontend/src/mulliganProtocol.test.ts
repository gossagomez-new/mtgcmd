import { describe, expect, it } from "vitest";
import { decodeSnapshot, ProtocolError } from "./protocol";
import { snapshotFixture } from "./protocolFixtures";

describe("opening-hand protocol", () => {
  const progress = {
    mode: "LONDON",
    freeMulligans: 1,
    players: [{ seat: 0, redraws: 2, kept: false, bottomCount: 1 }],
  };
  it("preserves validated opening settings and progress", () => {
    const view = decodeSnapshot({
      ...snapshotFixture(),
      status: "opening",
      mulligans: progress,
    });
    expect(view).toHaveProperty("mulligans", progress);
  });
  it.each([
    undefined,
    null,
    { ...progress, mode: "OTHER" },
    { ...progress, freeMulligans: -1 },
    { ...progress, players: [] },
    { ...progress, players: [progress.players[0], progress.players[0]] },
    { ...progress, players: [{ ...progress.players[0], seat: 9 }] },
    { ...progress, players: [{ ...progress.players[0], kept: true }] },
    { ...progress, mode: "UNLIMITED" },
    {
      ...progress,
      players: [{ seat: 0, redraws: -1, kept: false, bottomCount: 0 }],
    },
  ])("rejects malformed or absent opening metadata", (mulligans) => {
    expect(() =>
      decodeSnapshot({ ...snapshotFixture(), status: "opening", mulligans }),
    ).toThrow(ProtocolError);
  });
  it("accepts legacy playing snapshots without mulligan metadata", () => {
    expect(decodeSnapshot(snapshotFixture()).status).toBe("playing");
  });
});
