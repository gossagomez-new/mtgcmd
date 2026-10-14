import { describe, expect, it } from "vitest";
import type { Card } from "./protocol";
import { groupBattlefield } from "./battlefield";

function card(id: string, type: string, extra: Partial<Card> = {}): Card {
  return {
    id,
    owner: 0,
    controller: 0,
    zoneOwner: 0,
    zone: "battlefield",
    tapped: false,
    faceDown: false,
    face: 0,
    token: false,
    combat: "",
    x: 0,
    y: 0,
    counters: {},
    editable: true,
    stackOrder: 0,
    definition: {
      id,
      name: id,
      type,
      image: "",
      text: "",
      colors: [],
      legal: true,
      faces: [],
    },
    ...extra,
  };
}

describe("battlefield grouping", () => {
  it("inserts a new creature beside existing creatures and keeps within-group order", () => {
    const cards = [
      card("first", "Creature — Elf"),
      card("artifact", "Artifact"),
      card("second", "Creature"),
    ];
    expect(
      groupBattlefield(cards).map((group) => [
        group.type,
        group.cards.map((card) => card.id),
      ]),
    ).toEqual([
      ["creature", ["first", "second"]],
      ["artifact", ["artifact"]],
    ]);
  });

  it("uses primary-type precedence and places every land type last", () => {
    const cards = [
      card("land-creature", "Land Creature"),
      card("enchantment", "Enchantment"),
      card("artifact-creature", "Artifact Creature"),
      card("planeswalker", "Legendary Planeswalker"),
      card("battle", "Battle — Siege"),
      card("artifact-land", "Artifact Land"),
      card("artifact-enchantment", "Enchantment Artifact"),
      card("other", "Unknown"),
    ];
    expect(
      groupBattlefield(cards).map((group) => [
        group.type,
        group.cards.map((card) => card.id),
      ]),
    ).toEqual([
      ["creature", ["artifact-creature"]],
      ["planeswalker", ["planeswalker"]],
      ["battle", ["battle"]],
      ["artifact", ["artifact-enchantment"]],
      ["enchantment", ["enchantment"]],
      ["other", ["other"]],
      ["land", ["land-creature", "artifact-land"]],
    ]);
  });

  it("uses the active face and falls back to the definition when no face type exists", () => {
    const transformed = card("transformed", "Creature // Land");
    transformed.definition!.faces = [
      { name: "Front", type: "Creature", image: "", text: "" },
      { name: "Back", type: "Land", image: "", text: "" },
    ];
    transformed.face = 1;
    expect(groupBattlefield([transformed])[0].type).toBe("land");
    transformed.face = 0;
    expect(groupBattlefield([transformed])[0].type).toBe("creature");
    expect(groupBattlefield([card("fallback", "Artifact")])[0].type).toBe(
      "artifact",
    );
  });

  it("classifies face-down cards as Other even when their definition is known", () => {
    expect(
      groupBattlefield([
        card("hidden", "Land", { faceDown: true }),
        card("unknown", "", { definition: undefined }),
      ])[0].cards.map((card) => card.id),
    ).toEqual(["hidden", "unknown"]);
    expect(
      groupBattlefield([card("hidden", "Creature", { faceDown: true })])[0]
        .type,
    ).toBe("other");
  });

  it("matches card-type words without interpreting subtypes or substrings", () => {
    expect(
      groupBattlefield([
        card("subtype", "Creature — Land Artifact"),
        card("substring", "Artifactish"),
      ]).map((group) => group.type),
    ).toEqual(["creature", "other"]);
  });

  it("ignores saved coordinates, does not mutate input, and omits empty groups", () => {
    const cards = [
      card("artifact", "Artifact", { x: 0.9, y: 0.8 }),
      card("creature", "Creature"),
    ];
    const original = structuredClone(cards);
    Object.freeze(cards);
    cards.forEach(Object.freeze);
    expect(groupBattlefield(cards).map((group) => group.type)).toEqual([
      "creature",
      "artifact",
    ]);
    expect(cards).toEqual(original);
    expect(groupBattlefield([])).toEqual([]);
  });
});
