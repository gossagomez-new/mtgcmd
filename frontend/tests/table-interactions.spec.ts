import {
  test,
  expect,
  roomId,
  type TableFixture,
} from "./support/tableFixture";
import { snapshotFixture } from "../src/protocolFixtures";
import type { GameFormat, Snapshot } from "../src/protocol";

function table(format: GameFormat): Snapshot {
  const view = { ...snapshotFixture(format), id: roomId, abilities: [] };
  view.players = Array.from({ length: view.seatCapacity }, (_, seat) => ({
    ...view.players[0],
    seat,
    name: seat ? `Opponent ${seat}` : "Alice",
    privateAccess: seat === 0,
    counters: { life: seat ? -seat : 20 },
    commanders: [],
    counts: { library: 10, hand: 0, battlefield: 2 },
  }));
  view.cards = [
    {
      ...view.cards[0],
      id: "permanent",
      counters: {},
      commanderKey: format === "COMMANDER" ? "designated" : null,
    },
    { ...view.cards[1], id: "concealed" },
    {
      ...view.cards[0],
      id: "opponent",
      owner: 1,
      controller: 1,
      zoneOwner: 1,
      editable: false,
    },
  ];
  return view;
}

async function open(mockTable: TableFixture, format: GameFormat) {
  await mockTable.open(table(format));
  mockTable.onCommand = async (command) => {
    const next = {
      ...mockTable.view,
      revision: mockTable.view.revision + 1,
      log: [...mockTable.view.log, `Applied ${command.id}`],
      cards: mockTable.view.cards.map((card) => ({ ...card })),
    };
    if (command.type === "TAP") {
      const card = next.cards.find((card) => card.id === command.data.cardId)!;
      card.tapped = !card.tapped;
    }
    if (command.type === "UNTAP_ALL")
      next.cards.forEach((card) => {
        if (card.controller === next.you) card.tapped = false;
      });
    if (command.type === "END_TURN") {
      next.active = 1;
      next.turn++;
      next.phase = "Untap";
    }
    if (command.type === "MOVE")
      next.cards = next.cards.map((card) =>
        card.id === command.data.cardId
          ? { ...card, zone: String(command.data.zone) }
          : card,
      );
    await mockTable.acknowledge(next, command.id, async () => {
      await expect(mockTable.page.locator(".action-log li").first()).toHaveText(
        `Applied ${command.id}`,
      );
    });
  };
}

async function snapshot(mockTable: TableFixture, changes: Partial<Snapshot>) {
  const next = {
    ...mockTable.view,
    ...changes,
    revision: mockTable.view.revision + 1,
  };
  next.phase = `Snapshot ${next.revision}`;
  await mockTable.snapshot(next, async () => {
    await expect(mockTable.page.locator(".turn-strip")).toContainText(
      next.phase,
    );
  });
}

for (const format of ["STANDARD", "COMMANDER"] satisfies GameFormat[]) {
  test(`${format}: only the active player can use persistent turn controls`, async ({
    page,
    mockTable,
  }) => {
    await open(mockTable, format);
    const end = page.getByRole("button", { name: "End turn", exact: true });
    await expect(end).toBeEnabled();
    await page.locator(".board-scroll").evaluate((el) => {
      const spacer = document.createElement("div");
      spacer.style.height = "3000px";
      el.append(spacer);
      el.scrollTop = 2000;
    });
    const box = await end.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.y).toBeGreaterThanOrEqual(0);
    expect(box!.y + box!.height).toBeLessThan(1000);
    await expect(
      page.getByRole("button", { name: "Untap all", exact: true }),
    ).toBeInViewport();
    await end.click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({ type: "END_TURN", data: {} });
    await expect(end).toBeDisabled();
    await expect(
      page.getByRole("button", { name: "Next phase →" }),
    ).toBeDisabled();
    await snapshot(mockTable, { you: 1 });
    await expect(end).toBeEnabled();
    await snapshot(mockTable, { status: "finished" });
    await expect(end).toBeDisabled();
  });

  test(`${format}: double click taps once and single/keyboard clicks inspect`, async ({
    page,
    mockTable,
  }) => {
    await open(mockTable, format);
    const card = page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect Front", exact: true });
    await card.dblclick();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "TAP",
      data: { cardId: "permanent" },
    });
    await expect(card).toHaveClass(/tapped/);
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toHaveCount(0);
    await card.click();
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Close inspector" }).click();
    await card.focus();
    await page.keyboard.press("Enter");
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toBeVisible();
    expect(mockTable.commands).toHaveLength(1);
  });

  test(`${format}: battlefield dragging moves to a zone and cancels deferred inspection`, async ({
    page,
    mockTable,
  }) => {
    await open(mockTable, format);
    const card = page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect Front", exact: true });
    await expect(card).toHaveAttribute("draggable", "true");
    await card.dragTo(
      page
        .locator(".your-resources")
        .getByRole("button", { name: "graveyard 0", exact: true }),
    );
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "MOVE",
      data: { cardId: "permanent", zone: "graveyard" },
    });
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toHaveCount(0);
  });

  test(`${format}: drop boundaries ignore foreign/hidden IDs and read-only or pending cards`, async ({
    page,
    mockTable,
  }) => {
    await open(mockTable, format);
    const target = page
      .locator(".your-resources")
      .getByRole("button", { name: "exile 0", exact: true });
    for (const identifier of ["", "external", "concealed", "opponent"]) {
      await target.evaluate((el, id) => {
        const transfer = new DataTransfer();
        transfer.setData("application/card", id);
        el.dispatchEvent(
          new DragEvent("drop", { dataTransfer: transfer, bubbles: true }),
        );
      }, identifier);
    }
    expect(mockTable.commands).toHaveLength(0);
    const readonly = page
      .getByTestId("opponent-1")
      .getByRole("button", { name: "Inspect Front" });
    await readonly.dblclick();
    expect(mockTable.commands).toHaveLength(0);
    await page.getByRole("button", { name: "Close inspector" }).click();
    mockTable.onCommand = undefined;
    await page.getByRole("button", { name: "Untap all", exact: true }).click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    const card = page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect Front", exact: true });
    await expect(card).toHaveAttribute("draggable", "false");
    const drag = await card.evaluate((el) => {
      const transfer = new DataTransfer();
      const event = new DragEvent("dragstart", {
        dataTransfer: transfer,
        bubbles: true,
        cancelable: true,
      });
      el.dispatchEvent(event);
      return {
        prevented: event.defaultPrevented,
        payload: transfer.getData("application/card"),
      };
    });
    expect(drag).toEqual({ prevented: true, payload: "" });
    await card.dblclick();
    expect(mockTable.commands).toHaveLength(1);
  });

  test(`${format}: every supported destination receives one move with existing semantics`, async ({
    page,
    mockTable,
  }) => {
    await open(mockTable, format);
    const original = mockTable.view.cards;
    const zones = [
      "hand",
      "library",
      "exile",
      "stack",
      ...(format === "COMMANDER" ? ["command"] : []),
    ];
    for (const [index, zone] of zones.entries()) {
      await snapshot(mockTable, { cards: original });
      const source = page
        .getByTestId("your-battlefield")
        .getByRole("button", { name: "Inspect Front", exact: true });
      const destination =
        zone === "hand"
          ? page.locator("section.hand")
          : zone === "stack"
            ? page.locator("section.stack")
            : page
                .locator(".your-resources")
                .getByRole("button", { name: new RegExp(`^${zone} `) });
      await source.dragTo(destination);
      await expect.poll(() => mockTable.commands.length).toBe(index + 1);
      expect(mockTable.commands[index]).toMatchObject({
        type: "MOVE",
        data: { cardId: "permanent", zone },
      });
      await mockTable.settled();
    }
  });

  test(`${format}: a snapshot invalidates deferred inspection when the card loses permission`, async ({
    page,
    mockTable,
  }) => {
    await page.clock.install();
    await open(mockTable, format);
    const card = page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect Front", exact: true });
    await card.click();
    await snapshot(mockTable, {
      cards: mockTable.view.cards.map((c) => ({ ...c, editable: false })),
    });
    await page.clock.fastForward(600);
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toHaveCount(0);
    expect(mockTable.commands).toHaveLength(0);
  });

  test(`${format}: benign snapshots preserve deferred inspection with the latest definition`, async ({
    page,
    mockTable,
  }) => {
    await page.clock.install();
    await open(mockTable, format);
    await page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect Front", exact: true })
      .click();
    await snapshot(mockTable, {
      cards: mockTable.view.cards.map((c) =>
        c.id === "permanent" && c.definition
          ? {
              ...c,
              definition: {
                ...c.definition,
                faces: [{ ...c.definition.faces[0], name: "Updated face" }],
              },
            }
          : c,
      ),
    });
    await page.clock.fastForward(600);
    await expect(
      page
        .getByRole("dialog", { name: "Card inspector" })
        .getByRole("heading", { name: "Updated face", exact: true }),
    ).toBeVisible();
  });

  test(`${format}: opponent life stays adjacent and follows snapshots`, async ({
    page,
    mockTable,
  }) => {
    await open(mockTable, format);
    const identity = page.getByTestId("opponent-1").locator(".player-identity");
    await expect(identity).toContainText("Opponent 1");
    await expect(identity.locator(".opponent-life")).toHaveText("-1");
    const nameBox = await identity.locator(".player-name").boundingBox();
    const lifeBox = await identity.locator(".opponent-life").boundingBox();
    expect(lifeBox!.x - (nameBox!.x + nameBox!.width)).toBeLessThan(30);
    await snapshot(mockTable, {
      players: mockTable.view.players.map((p) =>
        p.seat === 1 ? { ...p, counters: { life: 0 } } : p,
      ),
    });
    await expect(identity.locator(".opponent-life")).toHaveText("0");
  });
}
