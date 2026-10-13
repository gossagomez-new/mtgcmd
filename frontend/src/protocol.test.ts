import { describe, it, expect } from "vitest";
import { acceptSnapshot, commandPayload, type Snapshot } from "./protocol";
describe("server snapshot sequencing", () => {
  const snapshot = (id: string, revision: number) =>
    ({ id, revision }) as Snapshot;
  it("ignores out of order snapshots but accepts other rooms and newer revisions", () => {
    const current = snapshot("a", 5);
    expect(acceptSnapshot(current, snapshot("a", 4))).toBe(current);
    expect(acceptSnapshot(current, snapshot("a", 6)).revision).toBe(6);
    expect(acceptSnapshot(current, snapshot("b", 1)).id).toBe("b");
  });
  it("creates independent command ids with the expected revision", () => {
    const a = commandPayload(5, "DRAW", { count: 1 });
    const b = commandPayload(5, "DRAW", { count: 1 });
    expect(a.id).not.toBe(b.id);
    expect(a).toMatchObject({ revision: 5, type: "DRAW", data: { count: 1 } });
  });
});
