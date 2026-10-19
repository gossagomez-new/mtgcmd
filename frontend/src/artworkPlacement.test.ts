import { describe, expect, it } from "vitest";
import { artworkPlacement } from "./artworkPlacement";

describe("artwork placement", () => {
  const card = {
    left: 100,
    right: 196,
    top: 100,
    bottom: 234,
    width: 96,
    height: 134,
  };
  it("prefers the right with a larger upright image", () => {
    expect(artworkPlacement(card, 1500, 1000)).toEqual({
      left: 204,
      top: 100,
      width: 280,
      height: 392,
    });
  });
  it("uses left when the right cannot fit an enlarged image", () => {
    expect(
      artworkPlacement({ ...card, left: 1200, right: 1296 }, 1350, 1000)?.left,
    ).toBe(912);
  });
  it("uses below or above when side space is insufficient", () => {
    expect(
      artworkPlacement(
        { left: 8, right: 148, top: 8, bottom: 204, width: 140, height: 196 },
        300,
        850,
      )?.top,
    ).toBe(212);
    expect(
      artworkPlacement(
        { left: 8, right: 148, top: 620, bottom: 816, width: 140, height: 196 },
        300,
        850,
      )?.top,
    ).toBe(220);
  });
  it("clamps to margins and omits cramped or offscreen sources", () => {
    const placement = artworkPlacement(
      { ...card, top: 700, bottom: 834 },
      390,
      850,
    )!;
    expect(placement.top + placement.height).toBeLessThanOrEqual(842);
    expect(placement.left + placement.width).toBeLessThanOrEqual(382);
    expect(artworkPlacement(card, 210, 250)).toBeNull();
    expect(
      artworkPlacement({ ...card, top: -200, bottom: -66 }, 1500, 1000),
    ).toBeNull();
  });
});
