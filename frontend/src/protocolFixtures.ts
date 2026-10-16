import type { CardDefinition, GameFormat, Snapshot } from "./protocol";

export function definitionFixture(): CardDefinition {
  return {
    id: "definition",
    name: "Private card name",
    image: "",
    text: "",
    type: "Creature",
    colors: [],
    legal: true,
    faces: [{ name: "Front", image: "", text: "", type: "Creature" }],
    legalities: { standard: "legal" },
    legalityCheckedAt: null,
  };
}

export function snapshotFixture(format: GameFormat = "COMMANDER"): Snapshot {
  return {
    id: "room",
    revision: 7,
    format,
    seatCapacity: format === "STANDARD" ? 2 : 4,
    status: "playing",
    host: 0,
    active: 0,
    turn: 1,
    phase: "Untap",
    you: 0,
    players: [
      {
        seat: 0,
        name: "Alice",
        ready: true,
        conceded: false,
        deckRevision: 1,
        privateAccess: true,
        deckCount: 60,
        warnings: [],
        counters: { life: 20, correction: -1 },
        counts: { library: 59 },
        commanders:
          format === "STANDARD"
            ? []
            : [{ key: "commander", name: "Commander", casts: 0 }],
      },
    ],
    cards: [
      {
        id: "card",
        definition: definitionFixture(),
        owner: 0,
        controller: 0,
        zoneOwner: 0,
        zone: "battlefield",
        tapped: false,
        faceDown: false,
        face: 0,
        token: false,
        commanderKey: null,
        attachedTo: null,
        combat: "",
        stackOrder: 0,
        x: 0,
        y: 0,
        counters: { correction: -1 },
        editable: true,
      },
      {
        id: "hidden",
        owner: 0,
        controller: 0,
        zoneOwner: 0,
        zone: "battlefield",
        tapped: false,
        faceDown: true,
        face: 0,
        token: false,
        commanderKey: null,
        attachedTo: null,
        combat: "",
        stackOrder: 0,
        x: 0,
        y: 0,
        counters: {},
        editable: false,
      },
    ],
    abilities: [
      { id: "ability", controller: 0, text: "Ability", stackOrder: 2 },
    ],
    log: ["Game started"],
    grants: [],
    search: null,
  };
}
