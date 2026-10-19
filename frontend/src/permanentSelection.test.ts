import { describe, expect, it } from "vitest";
import { snapshotFixture } from "./protocolFixtures";
import { selectionState } from "./permanentSelection";

describe("permanent selection", () => {
  it("preserves the exact selection and rejects stale, duplicate or noneditable members", () => {
    const cards = snapshotFixture().cards;
    expect(selectionState([], cards, false)).toEqual({
      valid: false,
      count: 0,
    });
    expect(selectionState(["card"], cards, false)).toEqual({
      valid: true,
      count: 1,
    });
    expect(selectionState(["card"], cards, true).valid).toBe(false);
    for (const ids of [
      ["card", "hidden"],
      ["card", "missing"],
      ["card", "card"],
    ]) {
      expect(selectionState(ids, cards, false).valid).toBe(false);
    }
    expect(
      selectionState(
        ["card"],
        cards.map((card) => ({ ...card, zone: "hand" })),
        false,
      ).valid,
    ).toBe(false);
    expect(
      selectionState(
        ["card"],
        cards.map((card) => ({ ...card, editable: false })),
        false,
      ).valid,
    ).toBe(false);
    expect(
      selectionState(
        Array.from({ length: 501 }, (_, index) => String(index)),
        cards,
        false,
      ).valid,
    ).toBe(false);
  });
});
