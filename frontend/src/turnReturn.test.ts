import { describe, expect, it } from "vitest";
import { decodeSnapshot } from "./protocol";
import { snapshotFixture } from "./protocolFixtures";
import { turnNotice } from "./turnNotification";

function storage() {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      values.set(key, value);
    },
  };
}

describe("returned turn protocol and notices", () => {
  it("retains valid visit/availability metadata and accepts legacy views", () => {
    const legacy = snapshotFixture();
    expect(decodeSnapshot(legacy)).toEqual(legacy);
    const current = { ...legacy, turnVisit: 0, canGiveBackTurn: true };
    expect(decodeSnapshot(current)).toMatchObject({
      turnVisit: 0,
      canGiveBackTurn: true,
    });
  });
  it("rejects malformed visit and availability fields", () => {
    for (const turnVisit of [-1, 0.5, "1", null, Number.MAX_SAFE_INTEGER + 1]) {
      expect(() =>
        decodeSnapshot({ ...snapshotFixture(), turnVisit }),
      ).toThrow();
    }
    for (const canGiveBackTurn of [1, "true", null]) {
      expect(() =>
        decodeSnapshot({ ...snapshotFixture(), canGiveBackTurn }),
      ).toThrow();
    }
  });
  it("announces a restored turn once and suppresses repeated or older visits", () => {
    const memory = storage();
    const view = { ...snapshotFixture(), turn: 2, turnVisit: 1 };
    expect(turnNotice(view, memory)).toBe("Your turn — turn 2");
    const returned = { ...view, turn: 1, turnVisit: 2 };
    expect(turnNotice(returned, memory)).toBe("Your turn — turn 1");
    const changedPhase = { ...returned, phase: "Main 2" };
    expect(turnNotice(changedPhase, memory)).toBe("");
    expect(turnNotice(view, memory)).toBe("");
    expect(turnNotice({ ...view, turnVisit: 3 }, memory)).toBe(
      "Your turn — turn 2",
    );
  });
  it("does not let legacy high-water marks suppress new visit notices", () => {
    const memory = storage();
    const view = { ...snapshotFixture(), turnVisit: 0 };
    memory.setItem(`mtgcmd:turn-notice:${view.id}:${view.you}`, "99");
    expect(turnNotice(view, memory)).toBe("Your turn — turn 1");
    expect(turnNotice(view, memory)).toBe("");
    expect(turnNotice(snapshotFixture(), memory)).toBe("");
    expect(turnNotice({ ...view, id: "other" }, memory)).toBe(
      "Your turn — turn 1",
    );
  });
});
