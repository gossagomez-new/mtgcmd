import { describe, it, expect } from "vitest";
import { snapshotFixture } from "./protocolFixtures";
import { dropCommand } from "./cardInteractions";

describe("card drop intent", () => {
  const card = snapshotFixture().cards[0];
  it.each(["hand", "library", "graveyard", "exile", "stack"])(
    "moves a visible editable permanent to %s",
    (zone) => {
      expect(dropCommand([card], card.id, zone)).toEqual({
        cardId: card.id,
        zone,
      });
    },
  );
  it("preserves hand-to-battlefield and designated-commander moves", () => {
    expect(
      dropCommand([{ ...card, zone: "hand" }], card.id, "battlefield"),
    ).toEqual({ cardId: card.id, zone: "battlefield" });
    expect(
      dropCommand([{ ...card, commanderKey: "key" }], card.id, "command"),
    ).toEqual({ cardId: card.id, zone: "command" });
  });
  it("ignores invalid and same-zone drops without inferring hidden identity", () => {
    for (const id of ["", "external"])
      expect(dropCommand([card], id, "exile")).toBeNull();
    expect(
      dropCommand([{ ...card, editable: false }], card.id, "exile"),
    ).toBeNull();
    expect(dropCommand([card], card.id, "battlefield")).toBeNull();
    expect(dropCommand([card], card.id, "other")).toBeNull();
    expect(
      dropCommand([{ ...card, commanderKey: null }], card.id, "command"),
    ).toBeNull();
    expect(
      dropCommand(
        [{ ...card, definition: undefined, faceDown: true }],
        card.id,
        "hand",
      ),
    ).toEqual({ cardId: card.id, zone: "hand" });
  });
});
