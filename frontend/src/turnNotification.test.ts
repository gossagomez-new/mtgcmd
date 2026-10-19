import { describe, expect, it } from "vitest";
import { snapshotFixture } from "./protocolFixtures";
import { turnNotice } from "./turnNotification";

describe("turn notification", () => {
  it("announces once per room, seat and active turn, never for phase updates", () => {
    const memory = new Map<string, string>();
    const storage = {
      getItem: (key: string) => memory.get(key) ?? null,
      setItem: (key: string, value: string) => {
        memory.set(key, value);
      },
    };
    const view = snapshotFixture();
    expect(turnNotice(view, storage)).toBe("Your turn — turn 1");
    const updated = { ...view, phase: "Main", revision: 8 };
    expect(turnNotice(updated, storage)).toBe("");
    expect(turnNotice({ ...view, active: 1 }, storage)).toBe("");
    expect(turnNotice({ ...view, turn: 2 }, storage)).toBe(
      "Your turn — turn 2",
    );
    expect(turnNotice({ ...view, turn: 1 }, storage)).toBe("");
    expect(turnNotice({ ...view, status: "finished", turn: 3 }, storage)).toBe(
      "",
    );
    expect(turnNotice({ ...view, id: "different" }, storage)).toBe(
      "Your turn — turn 1",
    );
  });
});

it("ignores other seats and nonplaying games without touching storage", () => {
  const storage = {
    getItem: () => {
      throw new Error("must not read");
    },
    setItem: () => {
      throw new Error("must not write");
    },
  };
  const view = snapshotFixture();
  for (const status of ["lobby", "finished"])
    expect(turnNotice({ ...view, status }, storage)).toBe("");
  expect(turnNotice({ ...view, active: 1 }, storage)).toBe("");
});
