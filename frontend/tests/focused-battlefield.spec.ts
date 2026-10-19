import {
  test,
  expect,
  roomId,
  type TableFixture,
} from "./support/tableFixture";
import { snapshotFixture, definitionFixture } from "../src/protocolFixtures";
import type { GameFormat, Snapshot } from "../src/protocol";

const art =
  'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="280"/>';
function table(format: GameFormat): Snapshot {
  const view = { ...snapshotFixture(format), id: roomId, abilities: [] };
  view.players = Array.from({ length: view.seatCapacity }, (_, seat) => ({
    ...view.players[0],
    seat,
    name: seat ? `Opponent ${seat}` : "Alice",
    privateAccess: seat === 0,
    counters: { life: 20 },
    counts: { battlefield: 1 },
    commanders: [],
  }));
  view.cards = [
    {
      ...view.cards[0],
      id: "opponent",
      owner: 1,
      controller: 1,
      zoneOwner: 1,
      editable: false,
      definition: { ...definitionFixture(), image: art, faces: [] },
    },
    { ...view.cards[1], owner: 1, controller: 1, zoneOwner: 1 },
  ];
  return view;
}
async function update(mock: TableFixture, changes: Partial<Snapshot>) {
  const next = {
    ...mock.view,
    ...changes,
    revision: mock.view.revision + 1,
    phase: `Focus ${mock.view.revision + 1}`,
  };
  await mock.snapshot(next, async () => {
    await expect(mock.page.locator(".turn-strip")).toContainText(next.phase);
  });
}
for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: focus is a popover and outside commands execute once`, async ({
    mockTable,
    page,
  }) => {
    await mockTable.open(table(format));
    const counts = mockTable.counts();
    const trigger = page.getByRole("button", {
      name: "Focus Opponent 1 battlefield",
    });
    await trigger.click();
    const focus = page.getByRole("dialog", { name: "Opponent 1 battlefield" });
    await expect(focus).toHaveAttribute("popover", "auto");
    expect(
      await focus.evaluate((element) => element.matches(":popover-open")),
    ).toBe(true);
    await expect(focus).toHaveAttribute("aria-modal", "false");
    await expect(trigger).toHaveAttribute("aria-expanded", "true");
    await expect(
      focus.getByRole("button", { name: "Close focused battlefield" }),
    ).toBeFocused();
    const end = page.getByRole("button", { name: "End turn", exact: true });
    await end.click({ timeout: 5000 });
    await expect(focus).toHaveCount(0);
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0].type).toBe("END_TURN");
    await expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(mockTable.counts()).toEqual(counts);
  });
  test(`${format}: keyboard, nested menu, details and Close preserve focus`, async ({
    mockTable,
    page,
  }) => {
    await mockTable.open(table(format));
    const trigger = page.getByRole("button", {
      name: "Focus Opponent 1 battlefield",
    });
    await trigger.focus();
    await page.keyboard.press("Enter");
    const focus = page.getByRole("dialog", { name: "Opponent 1 battlefield" });
    await expect(focus).toHaveAttribute("popover", "auto");
    await focus
      .getByRole("button", { name: "Actions for Private card name" })
      .click();
    await expect(
      page.getByRole("menu", { name: "Card actions" }),
    ).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("menu", { name: "Card actions" })).toHaveCount(
      0,
    );
    await expect(focus).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(focus).toHaveCount(0);
    await expect(trigger).toBeFocused();
    await trigger.click();
    await focus
      .getByRole("button", { name: "Actions for Private card name" })
      .click();
    await page.getByRole("menuitem", { name: "Details", exact: true }).click();
    await expect(focus).toHaveCount(0);
    const details = page.getByRole("dialog", { name: "Card inspector" });
    await expect(details).toBeVisible();
    await details.getByRole("button", { name: "Close inspector" }).click();
    await expect(trigger).toBeFocused();
    await trigger.click();
    await focus
      .getByRole("button", { name: "Close focused battlefield" })
      .click();
    await expect(trigger).toBeFocused();
    expect(mockTable.commands).toHaveLength(0);
  });
  test(`${format}: focus resizes, stays private/live and removes missing opponents`, async ({
    mockTable,
    page,
  }) => {
    await mockTable.open(table(format));
    const counts = mockTable.counts();
    await page
      .getByRole("button", { name: "Focus Opponent 1 battlefield" })
      .click();
    const focus = page.getByRole("dialog", { name: "Opponent 1 battlefield" });
    await expect(focus).toHaveAttribute("popover", "auto");
    await expect(
      focus.getByRole("button", { name: "Inspect Face-down card" }),
    ).toBeVisible();
    await update(mockTable, {
      players: mockTable.view.players.map((player) =>
        player.seat === 1 ? { ...player, counters: { life: 7 } } : player,
      ),
      cards: mockTable.view.cards.map((card) => ({
        ...card,
        definition: undefined,
        faceDown: true,
      })),
    });
    await expect(focus.getByLabel("Opponent 1 life")).toHaveText("7");
    await expect(focus.locator(".card img")).toHaveCount(0);
    for (const viewport of [
      { width: 390, height: 780 },
      { width: 720, height: 450 },
    ]) {
      await page.setViewportSize(viewport);
      await expect
        .poll(async () => {
          const box = (await focus.boundingBox())!;
          return (
            box.x >= 8 &&
            box.y >= 8 &&
            box.x + box.width <= viewport.width - 8 &&
            box.y + box.height <= viewport.height - 8
          );
        })
        .toBe(true);
    }
    await update(mockTable, {
      players: mockTable.view.players.filter((player) => player.seat !== 1),
      cards: [],
    });
    await expect(focus).toHaveCount(0);
    expect(mockTable.counts()).toEqual(counts);
    expect(mockTable.commands).toHaveLength(0);
  });
}

test("Commander replaces a focused opponent without losing the table session", async ({
  mockTable,
  page,
}) => {
  await mockTable.open(table("COMMANDER"));
  const counts = mockTable.counts();
  await page
    .getByRole("button", { name: "Focus Opponent 1 battlefield" })
    .click();
  await expect(
    page.getByRole("dialog", { name: "Opponent 1 battlefield" }),
  ).toHaveAttribute("popover", "auto");
  await page
    .getByRole("button", { name: "Focus Opponent 2 battlefield" })
    .click();
  await expect(
    page.getByRole("dialog", { name: "Opponent 1 battlefield" }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("dialog", { name: "Opponent 2 battlefield" }),
  ).toBeVisible();
  expect(mockTable.counts()).toEqual(counts);
});

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: Tab can leave focused battlefield and Escape dismisses from the background`, async ({
    mockTable,
    page,
  }) => {
    await mockTable.open(table(format));
    const trigger = page.getByRole("button", {
      name: "Focus Opponent 1 battlefield",
    });
    await trigger.click();
    const focus = page.getByRole("dialog", { name: "Opponent 1 battlefield" });
    await page.keyboard.press("Shift+Tab");
    await expect
      .poll(() =>
        focus.evaluate((element) => element.contains(document.activeElement)),
      )
      .toBe(false);
    await expect(focus).toBeVisible();
    await trigger.focus();
    await page.keyboard.press("Escape");
    await expect(focus).toHaveCount(0);
    await expect(trigger).toBeFocused();
    expect(mockTable.commands).toHaveLength(0);
  });
}
