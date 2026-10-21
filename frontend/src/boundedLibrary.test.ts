import { describe, expect, it } from "vitest";
import { decodeSnapshot } from "./protocol";
import { snapshotFixture } from "./protocolFixtures";

describe("bounded library protocol", () => {
  it("retains actor-only inspection metadata and accepts old/null snapshots", () => {
    const view = snapshotFixture();
    const libraryInspection = {
      id: "session",
      target: 0,
      mode: "scry",
      cardIds: ["one", "two"],
    };
    expect(decodeSnapshot({ ...view, libraryInspection })).toMatchObject({
      libraryInspection,
    });
    expect(decodeSnapshot(view)).toEqual(view);
    expect(decodeSnapshot({ ...view, libraryInspection: null })).toMatchObject({
      libraryInspection: null,
    });
  });
  it("rejects malformed nested scopes before using the snapshot", () => {
    const base = { id: "session", target: 0, mode: "look", cardIds: ["one"] };
    for (const invalid of [
      { ...base, id: "" },
      { ...base, target: -1 },
      { ...base, mode: "search" },
      { ...base, cardIds: [] },
      { ...base, cardIds: ["one", "one"] },
      { ...base, cardIds: [1] },
      { ...base, cardIds: [""] },
      { ...base, cardIds: Array.from({ length: 101 }, (_, i) => String(i)) },
    ])
      expect(() =>
        decodeSnapshot({ ...snapshotFixture(), libraryInspection: invalid }),
      ).toThrow();
  });
});
