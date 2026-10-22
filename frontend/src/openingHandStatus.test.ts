import { describe, expect, it } from "vitest";
import { openingHandWaitingMessage } from "./openingHandStatus";
import { snapshotFixture } from "./protocolFixtures";

describe("opening-hand waiting message", () => {
  it.each([
    {
      names: ["Alice", "Bob"],
      kept: [true, false],
      you: 0,
      expected: "Waiting for Bob to keep their opening hand.",
    },
    {
      names: ["Alice", "Bob", "Carol", "Dan"],
      kept: [true, false, true, false],
      you: 0,
      expected: "Waiting for Bob and Dan to keep their opening hands.",
    },
    {
      names: ["Alice", "Bob", "Carol", "Dan"],
      kept: [false, false, false, true],
      you: 3,
      expected:
        "Waiting for Alice, Bob, and Carol to keep their opening hands.",
    },
    {
      names: ["Alex", "Alex", "Carol", "Dana"],
      kept: [false, false, true, true],
      you: 3,
      expected: "Waiting for Alex and Alex to keep their opening hands.",
    },
    {
      names: ["Alice", "Bob"],
      kept: [false, false],
      you: 1,
      expected: "Waiting for Alice to keep their opening hand.",
    },
    {
      names: ["Alice", "Bob"],
      kept: [true, true],
      you: 0,
      expected: "Waiting for the game to begin.",
    },
  ])(
    "formats seats and progress: $expected",
    ({ names, kept, you, expected }) => {
      const view = snapshotFixture(
        names.length === 2 ? "STANDARD" : "COMMANDER",
      );
      view.you = you;
      view.players = names.map((name, seat) => ({
        ...view.players[0],
        name,
        seat,
      }));
      view.mulligans = {
        mode: "LONDON",
        freeMulligans: 0,
        players: kept
          .map((value, seat) => ({
            seat,
            kept: value,
            redraws: 0,
            bottomCount: 0,
          }))
          .reverse(),
      };
      expect(openingHandWaitingMessage(view)).toBe(expected);
    },
  );

  it("handles snapshots without opening progress", () => {
    expect(openingHandWaitingMessage(snapshotFixture())).toBe(
      "Waiting for the game to begin.",
    );
  });
});
