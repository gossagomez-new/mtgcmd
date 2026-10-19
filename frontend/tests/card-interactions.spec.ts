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
    counters: { life: 20 },
    commanders: [],
    counts: { battlefield: 2 },
  }));
  const first = {
    ...view.cards[0],
    counters: {},
    definition: {
      ...view.cards[0].definition!,
      name: "First permanent",
      faces: [],
    },
  };
  view.cards = [
    first,
    {
      ...first,
      id: "second",
      definition: { ...first.definition, name: "Second permanent" },
      tapped: true,
    },
    {
      ...first,
      id: "opponent",
      owner: 1,
      controller: 1,
      zoneOwner: 1,
      editable: false,
    },
    {
      ...view.cards[1],
      id: "concealed",
      owner: 1,
      controller: 1,
      zoneOwner: 1,
    },
  ];
  return view;
}
async function update(mock: TableFixture, changes: Partial<Snapshot>) {
  const next = {
    ...mock.view,
    ...changes,
    revision: mock.view.revision + 1,
    phase: `Update ${mock.view.revision}`,
  };
  await mock.snapshot(next, async () => {
    await expect(mock.page.locator(".turn-strip")).toContainText(next.phase);
  });
}
for (const format of ["STANDARD", "COMMANDER"] satisfies GameFormat[]) {
  test(`${format}: contextual actions are accessible and modal details restore focus`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const card = page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect First permanent", exact: true });
    await card.click({ button: "right" });
    const menu = page.getByRole("menu", { name: "Card actions" });
    await expect(menu).toBeVisible();
    await page.keyboard.press("End");
    await expect(menu.getByRole("menuitem", { name: "Details" })).toBeFocused();
    await page.keyboard.press("Enter");
    const details = page.getByRole("dialog", { name: "Card inspector" });
    await expect(details).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(details).toHaveCount(0);
    await expect(card).toBeFocused();
    await card.dblclick();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0].type).toBe("TAP");
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toHaveCount(0);
  });
  test(`${format}: selection submits one explicit batch and stale selections cannot partially submit`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    await page
      .getByRole("checkbox", { name: "Select First permanent", exact: true })
      .check();
    await page
      .getByRole("checkbox", { name: "Select Second permanent", exact: true })
      .check();
    await page
      .getByRole("button", { name: "Untap selected", exact: true })
      .click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "TAP_SELECTED",
      data: { cardIds: ["card", "second"], tapped: false },
    });
    const batch = mockTable.commands[0];
    await mockTable.acknowledge(
      { ...mockTable.view, revision: mockTable.view.revision + 1 },
      batch.id,
      async () => {
        await expect(
          page.getByRole("button", { name: "Tap selected", exact: true }),
        ).toBeEnabled();
      },
    );
    await update(mockTable, {
      cards: mockTable.view.cards.filter((card) => card.id !== "second"),
    });
    await expect(
      page.getByRole("button", { name: "Tap selected", exact: true }),
    ).toBeDisabled();
    await page
      .getByRole("button", { name: "Clear selection", exact: true })
      .click();
    await expect(page.getByText("0 selected", { exact: true })).toBeVisible();
  });
  test(`${format}: focused opponent view is live, private and does not reconnect`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const counts = mockTable.counts();
    const normalCard = await page
      .getByTestId("opponent-1")
      .locator(".card")
      .first()
      .boundingBox();
    await page
      .getByRole("button", {
        name: "Focus Opponent 1 battlefield",
        exact: true,
      })
      .click();
    const focus = page.getByRole("dialog", { name: "Opponent 1 battlefield" });
    await expect(focus).toBeVisible();
    const enlargedCard = await focus.locator(".card").first().boundingBox();
    expect(enlargedCard!.width).toBeGreaterThan(normalCard!.width);
    await expect(focus.getByLabel("Opponent 1 life")).toHaveText("20");
    await expect(
      focus.getByRole("button", { name: "Inspect Face-down card" }),
    ).toBeVisible();
    await focus
      .getByRole("button", { name: "Actions for First permanent" })
      .click();
    await expect(
      page.getByRole("menuitem", { name: "Tap", exact: true }),
    ).toHaveCount(0);
    await page.keyboard.press("Escape");
    await update(mockTable, {
      players: mockTable.view.players.map((p) =>
        p.seat === 1 ? { ...p, counters: { life: 8 } } : p,
      ),
    });
    await expect(focus.getByLabel("Opponent 1 life")).toHaveText("8");
    await page.keyboard.press("Escape");
    await expect(focus).toHaveCount(0);
    expect(mockTable.counts()).toEqual(counts);
    expect(mockTable.commands).toHaveLength(0);
  });
  test(`${format}: attachment details show current target and explicit detach`, async ({
    page,
    mockTable,
  }) => {
    const view = table(format);
    view.cards[0].attachedTo = "second";
    await mockTable.open(view);
    await page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect First permanent", exact: true })
      .press("Enter");
    const details = page.getByRole("dialog", { name: "Card inspector" });
    await expect(
      details.getByText("Attached to: Second permanent · Alice", {
        exact: true,
      }),
    ).toBeVisible();
    await details.getByRole("button", { name: "Detach", exact: true }).click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "ATTACH",
      data: { cardId: "card", targetId: "" },
    });
  });
  test(`${format}: turn notice does not repeat on phase snapshots or reload`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const notice = page.getByRole("status", { name: "Turn notification" });
    await expect(notice).toHaveText("Your turn — turn 1");
    await update(mockTable, {});
    await expect(notice).toHaveText("Your turn — turn 1");
    await page.reload();
    await expect(notice).toBeEmpty();
    await update(mockTable, { active: 1 });
    await expect(notice).toBeEmpty();
    await update(mockTable, { active: 0, turn: 2 });
    await expect(notice).toHaveText("Your turn — turn 2");
  });
}

for (const format of ["STANDARD", "COMMANDER"] satisfies GameFormat[]) {
  test(`${format}: menu follows live permission changes and cancels pending inspection`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const card = page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect First permanent", exact: true });
    await card.click();
    await card.click({ button: "right" });
    const menu = page.getByRole("menu", { name: "Card actions" });
    await expect(
      menu.getByRole("menuitem", { name: "Tap", exact: true }),
    ).toBeVisible();
    await expect(
      menu.getByRole("menuitem", { name: "Tap", exact: true }),
    ).toBeFocused();
    await page.keyboard.press("ArrowDown");
    await expect(
      menu.getByRole("menuitem", { name: "Exile face down" }),
    ).toBeFocused();
    await page.keyboard.press("Home");
    await expect(
      menu.getByRole("menuitem", { name: "Tap", exact: true }),
    ).toBeFocused();
    await update(mockTable, {
      cards: mockTable.view.cards.map((c) =>
        c.id === "card" ? { ...c, editable: false } : c,
      ),
    });
    await expect(
      menu.getByRole("menuitem", { name: "Tap", exact: true }),
    ).toHaveCount(0);
    await page.keyboard.press("Escape");
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toHaveCount(0);
    expect(mockTable.commands).toHaveLength(0);
    await card.click({ button: "right" });
    await update(mockTable, {
      cards: mockTable.view.cards.filter((c) => c.id !== "card"),
    });
    await expect(menu).toHaveCount(0);
  });

  test(`${format}: tap selection sends an explicit state once and recovers from rejection`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    await page
      .getByRole("checkbox", { name: "Select First permanent", exact: true })
      .check();
    const tap = page.getByRole("button", { name: "Tap selected", exact: true });
    await tap.click();
    await expect(tap).toBeDisabled();
    expect(mockTable.commands).toHaveLength(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "TAP_SELECTED",
      data: { cardIds: ["card"], tapped: true },
    });
    mockTable.sendRaw(
      JSON.stringify({
        type: "error",
        message: "Selection changed",
        view: mockTable.view,
      }),
    );
    await expect(page.getByRole("alert")).toContainText("Selection changed");
    await expect(tap).toBeEnabled();
    await expect(
      page
        .getByTestId("your-battlefield")
        .getByRole("button", { name: "Inspect First permanent", exact: true }),
    ).not.toHaveClass(/tapped/);
    await update(mockTable, { status: "finished" });
    await expect(tap).toBeDisabled();
    expect(mockTable.commands).toHaveLength(1);
  });

  test(`${format}: attachment target labels preserve privacy and use explicit commands`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    await page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Actions for First permanent", exact: true })
      .click();
    await page.getByRole("menuitem", { name: "Details" }).click();
    const details = page.getByRole("dialog", { name: "Card inspector" });
    const targets = details.getByRole("combobox", {
      name: "Attach to",
      exact: true,
    });
    await expect(targets).toBeVisible();
    await expect(targets.locator('option[value="card"]')).toHaveCount(0);
    await expect(targets.locator('option[value="concealed"]')).toHaveText(
      "Face-down card · Opponent 1",
    );
    await expect(
      details.getByRole("button", { name: "Attach", exact: true }),
    ).toBeDisabled();
    await targets.selectOption("second");
    await details.getByRole("button", { name: "Attach", exact: true }).click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "ATTACH",
      data: { cardId: "card", targetId: "second" },
    });
    await mockTable.acknowledge(
      {
        ...mockTable.view,
        revision: mockTable.view.revision + 1,
        cards: mockTable.view.cards.map((c) =>
          c.id === "card" ? { ...c, attachedTo: "second" } : c,
        ),
      },
      mockTable.commands[0].id,
      async () => {
        await expect(
          details.getByText("Attached to: Second permanent · Alice", {
            exact: true,
          }),
        ).toBeVisible();
      },
    );
    await update(mockTable, {
      cards: mockTable.view.cards.map((c) =>
        c.id === "card" ? { ...c, editable: false } : c,
      ),
    });
    await expect(
      details.getByRole("button", { name: "Detach", exact: true }),
    ).toHaveCount(0);
    await expect(
      details.getByText("Attached to: Second permanent · Alice", {
        exact: true,
      }),
    ).toBeVisible();
    await page.keyboard.press("Escape");
  });

  test(`${format}: turn notice tolerates blocked storage and ignores ordinary updates`, async ({
    page,
    mockTable,
  }) => {
    await page.addInitScript(() => {
      Storage.prototype.getItem = () => {
        throw new Error("Storage blocked");
      };
      Storage.prototype.setItem = () => {
        throw new Error("Storage blocked");
      };
    });
    // The app also reads localStorage for the player's name, so isolate the denial to session storage.
    await page.addInitScript(() => {
      Object.defineProperty(window, "localStorage", {
        value: { getItem: () => null, setItem: () => {} },
      });
    });
    await mockTable.open(table(format));
    const notice = page.getByRole("status", { name: "Turn notification" });
    await expect(notice).toHaveText("Your turn — turn 1");
    await update(mockTable, { active: 1 });
    await expect(notice).toBeEmpty();
    await update(mockTable, { active: 0 });
    await expect(notice).toHaveText("Your turn — turn 1");
    await update(mockTable, { turn: 2 });
    await expect(notice).toHaveText("Your turn — turn 2");
    await update(mockTable, { status: "finished" });
    await expect(notice).toBeEmpty();
  });
}

for (const format of ["STANDARD", "COMMANDER"] satisfies GameFormat[]) {
  test(`${format}: active player colour and label follow live turns in both board views`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const own = page.locator(".your-field");
    const other = page.getByTestId("opponent-1");
    await expect(own).toHaveClass(/active-player/);
    await expect(own.getByText("Active turn", { exact: true })).toBeVisible();
    await expect(other).not.toHaveClass(/active-player/);
    const ownColour = await own.evaluate(
      (el) => getComputedStyle(el).backgroundImage,
    );
    const inactiveColour = await other.evaluate(
      (el) => getComputedStyle(el).backgroundImage,
    );
    expect(ownColour).not.toBe(inactiveColour);
    await update(mockTable, { active: 1, turn: 2 });
    await expect(own).not.toHaveClass(/active-player/);
    await expect(other).toHaveClass(/active-player/);
    await page
      .getByRole("button", { name: "Focus Opponent 1 battlefield" })
      .click();
    const focus = page.getByRole("dialog", { name: "Opponent 1 battlefield" });
    await expect(focus.getByText("Active turn", { exact: true })).toBeVisible();
    if (format === "COMMANDER")
      await focus.screenshot({ path: "test-results/focused-opponent.png" });
    await update(mockTable, { status: "finished" });
    await expect(focus.getByTestId("opponent-1")).not.toHaveClass(
      /active-player/,
    );
    await expect(focus.getByText("Active turn", { exact: true })).toHaveCount(
      0,
    );
  });
}

test("selection and action controls stay outside card artwork", async ({
  page,
  mockTable,
}) => {
  await mockTable.open(table("STANDARD"));
  const slot = page
    .getByTestId("your-battlefield")
    .locator(".battlefield-card-slot")
    .first();
  const label = await slot.locator(".permanent-selection").boundingBox();
  const card = await slot.locator(".card").boundingBox();
  const actions = await slot
    .getByRole("button", { name: "Actions for First permanent" })
    .boundingBox();
  expect(label!.y + label!.height).toBeLessThanOrEqual(card!.y);
  expect(actions!.y).toBeGreaterThanOrEqual(card!.y + card!.height);
});

for (const format of ["STANDARD", "COMMANDER"] satisfies GameFormat[]) {
  test(`${format}: zone navigation from focused view opens an accessible viewer`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const counts = mockTable.counts();
    await page
      .getByRole("button", { name: "Focus Opponent 1 battlefield" })
      .click();
    const focus = page.getByRole("dialog", { name: "Opponent 1 battlefield" });
    await focus
      .getByRole("button", { name: "graveyard 0", exact: true })
      .click();
    await expect(focus).toHaveCount(0);
    const viewer = page.getByRole("dialog", { name: "Zone viewer" });
    await expect(viewer).toBeVisible();
    await viewer.getByRole("button", { name: "Close zone viewer" }).click();
    expect(mockTable.counts()).toEqual(counts);
    expect(mockTable.commands).toHaveLength(0);
  });
}

test("card actions are a non-modal popover with light dismissal and viewport placement", async ({
  page,
  mockTable,
}) => {
  await mockTable.open(table("STANDARD"));
  const trigger = page
    .getByTestId("your-battlefield")
    .getByRole("button", { name: "Actions for First permanent" });
  await trigger.click();
  const menu = page.getByRole("menu", { name: "Card actions" });
  await expect(menu).toBeVisible();
  await expect(page.getByRole("dialog", { name: "Card actions" })).toHaveCount(
    0,
  );
  await expect(
    page.getByRole("button", { name: "End turn", exact: true }),
  ).toBeEnabled();
  const box = (await menu.boundingBox())!;
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.y).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(1500);
  expect(box.y + box.height).toBeLessThanOrEqual(1000);
  await page.setViewportSize({ width: 390, height: 780 });
  await expect
    .poll(async () => {
      const resized = (await menu.boundingBox())!;
      return (
        resized.x >= 0 &&
        resized.y >= 0 &&
        resized.x + resized.width <= 390 &&
        resized.y + resized.height <= 780
      );
    })
    .toBe(true);
  await page.setViewportSize({ width: 1500, height: 1000 });
  await page.getByRole("button", { name: "End turn", exact: true }).click();
  await expect(menu).toHaveCount(0);
  await expect.poll(() => mockTable.commands.length).toBe(1);
  expect(mockTable.commands[0].type).toBe("END_TURN");
});
