import {
  test,
  expect,
  roomId,
  type TableFixture,
} from "./support/tableFixture";
import { snapshotFixture } from "../src/protocolFixtures";
import type { GameFormat, Snapshot } from "../src/protocol";

function table(format: GameFormat): Snapshot {
  const view = {
    ...snapshotFixture(format),
    id: roomId,
    turn: 2,
    active: 0,
    turnVisit: 3,
    canGiveBackTurn: true,
  };
  view.players = Array.from({ length: view.seatCapacity }, (_, seat) => ({
    ...view.players[0],
    seat,
    name: `Player ${seat}`,
    privateAccess: seat === 0,
  }));
  return view;
}
async function update(mock: TableFixture, changes: Partial<Snapshot>) {
  const next = {
    ...mock.view,
    ...changes,
    revision: mock.view.revision + 1,
    phase: `Update ${mock.view.revision + 1}`,
  };
  await mock.snapshot(next, async () => {
    await expect(mock.page.locator(".turn-strip")).toContainText(next.phase);
  });
}
for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: return submits once, rejects safely and follows authoritative eligibility`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const button = page.getByRole("button", {
      name: "Give back turn",
      exact: true,
    });
    await expect(button).toBeEnabled();
    await button.click();
    await expect(button).toBeDisabled();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "GIVE_BACK_TURN",
      revision: mockTable.view.revision,
      data: {},
    });
    mockTable.sendRaw(
      JSON.stringify({
        type: "error",
        message: "Previous player has conceded",
        view: { ...mockTable.view, canGiveBackTurn: false },
      }),
    );
    await expect(page.getByRole("alert")).toContainText(
      "Previous player has conceded",
    );
    await expect(button).toBeDisabled();
    expect(mockTable.commands).toHaveLength(1);
    await update(mockTable, { active: 1, canGiveBackTurn: true });
    await expect(button).toBeDisabled();
    await update(mockTable, { active: 0, canGiveBackTurn: true });
    await expect(button).toBeEnabled();
    await update(mockTable, { status: "finished" });
    await expect(button).toBeDisabled();
    expect(mockTable.commands).toHaveLength(1);
  });
  test(`${format}: restored turn notices survive phase updates and deduplicate across reload`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const counts = mockTable.counts();
    const notice = page.getByRole("status", { name: "Turn notification" });
    await expect(notice).toHaveText("Your turn — turn 2");
    await update(mockTable, {
      active: 1,
      turn: 3,
      turnVisit: 4,
      canGiveBackTurn: false,
    });
    await expect(notice).toBeEmpty();
    await update(mockTable, {
      active: 0,
      turn: 2,
      turnVisit: 5,
      canGiveBackTurn: true,
    });
    await expect(notice).toHaveText("Your turn — turn 2");
    await update(mockTable, {});
    await expect(notice).toHaveText("Your turn — turn 2");
    expect(mockTable.counts()).toEqual(counts);
    await page.reload();
    await expect(page.getByText("Connected", { exact: true })).toBeVisible();
    await expect(notice).toBeEmpty();
    await update(mockTable, { turn: 1, turnVisit: 6 });
    await expect(notice).toHaveText("Your turn — turn 1");
    expect(mockTable.commands).toHaveLength(0);
  });
  test(`${format}: a successful return updates controls without changing cards`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const cards = JSON.stringify(mockTable.view.cards);
    const counts = mockTable.counts();
    const button = page.getByRole("button", {
      name: "Give back turn",
      exact: true,
    });
    await button.click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    const result = {
      ...mockTable.view,
      revision: mockTable.view.revision + 1,
      active: 1,
      turn: 1,
      turnVisit: 4,
      phase: "Main 2",
      canGiveBackTurn: false,
    };
    await mockTable.acknowledge(result, mockTable.commands[0].id, async () => {
      await expect(page.locator(".turn-strip")).toContainText("Main 2");
    });
    await expect(button).toBeDisabled();
    expect(JSON.stringify(mockTable.view.cards)).toBe(cards);
    expect(mockTable.counts()).toEqual(counts);
    await page.reload();
    await expect(page.locator(".turn-strip")).toContainText("Main 2");
    expect(mockTable.commands).toHaveLength(1);
  });
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: legacy views keep return disabled`, async ({
    page,
    mockTable,
  }) => {
    const legacy = table(format);
    delete legacy.turnVisit;
    delete legacy.canGiveBackTurn;
    await mockTable.open(legacy);
    await expect(
      page.getByRole("button", { name: "Give back turn", exact: true }),
    ).toBeDisabled();
    expect(mockTable.commands).toHaveLength(0);
  });
  test(`${format}: blocked storage deduplicates returned visits within the session`, async ({
    page,
    mockTable,
  }) => {
    await page.addInitScript(() => {
      Object.defineProperty(window, "sessionStorage", {
        get() {
          throw new Error("blocked");
        },
      });
    });
    await mockTable.open(table(format));
    const notice = page.getByRole("status", { name: "Turn notification" });
    await expect(notice).toHaveText("Your turn — turn 2");
    await update(mockTable, { active: 1, turnVisit: 4 });
    await expect(notice).toBeEmpty();
    await update(mockTable, { active: 0, turn: 1, turnVisit: 5 });
    await expect(notice).toHaveText("Your turn — turn 1");
    await update(mockTable, {});
    await expect(notice).toHaveText("Your turn — turn 1");
    expect(mockTable.commands).toHaveLength(0);
  });
}
