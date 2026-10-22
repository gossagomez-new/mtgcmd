import {
  test,
  expect,
  roomId,
  type TableFixture,
} from "./support/tableFixture";
import { snapshotFixture } from "../src/protocolFixtures";
import type { GameFormat, Snapshot } from "../src/protocol";

async function update(mockTable: TableFixture, next: Snapshot) {
  const phase = `App fixture ${next.revision}`;
  await mockTable.acknowledge({ ...next, phase }, "fixture", async () => {
    await expect(mockTable.page.locator(".turn-strip")).toContainText(phase);
  });
}

for (const format of ["COMMANDER", "STANDARD"] satisfies GameFormat[]) {
  test(`${format}: screen transitions keep playing content for finished and other statuses`, async ({
    page,
    mockTable,
  }) => {
    const view = { ...snapshotFixture(format), id: roomId, status: "lobby" };
    await mockTable.open(view);
    await expect(page.locator("main.lobby")).toBeVisible();
    await expect(page.locator("main.welcome")).toHaveCount(0);
    for (const [offset, status] of [
      "playing",
      "finished",
      "paused",
    ].entries()) {
      await update(mockTable, {
        ...view,
        revision: view.revision + offset + 1,
        status,
      });
      await expect(page.locator("main.game-layout")).toBeVisible();
      await expect(page.locator("main.lobby")).toHaveCount(0);
      await expect(
        page.getByRole("button", { name: "Draw a card", exact: true }),
      ).toBeEnabled({ enabled: status !== "finished" });
      await expect(page.locator(".finished-banner")).toHaveCount(
        status === "finished" ? 1 : 0,
      );
    }
    expect(mockTable.counts()).toEqual({ sessions: 1, connections: 1 });
  });
}

test("error dismissal and finished notices preserve placement around screens and overlays", async ({
  page,
  mockTable,
}) => {
  const view = { ...snapshotFixture("STANDARD"), id: roomId };
  await mockTable.open(view);
  mockTable.sendRaw(JSON.stringify({ type: "error", message: "Test notice" }));
  await expect(page.getByRole("alert")).toHaveText("Test notice×");
  await expect(
    page.locator(".topbar + .error-banner + main.game-layout"),
  ).toBeVisible();
  await page.getByRole("button", { name: "Dismiss error" }).click();
  await expect(page.getByRole("alert")).toHaveCount(0);
  await page.getByRole("button", { name: "graveyard 0", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Zone viewer" })).toBeVisible();
  await update(mockTable, { ...view, revision: 8, status: "finished" });
  mockTable.sendRaw(
    JSON.stringify({ type: "error", message: "Finished notice" }),
  );
  await expect(page.getByRole("alert")).toContainText("Finished notice");
  await expect(
    page.locator("main.game-layout + .finished-banner + dialog.zone-modal"),
  ).toBeVisible();
  await expect(page.locator(".finished-banner")).toHaveText(
    "Game finished. Your table has been saved.",
  );
  await page.getByRole("button", { name: "Close zone viewer" }).click();
  await page.getByRole("button", { name: "Dismiss error" }).click();
  await expect(page.locator(".finished-banner")).toBeVisible();
  expect(mockTable.counts()).toEqual({ sessions: 1, connections: 1 });
});

test("library command ordering, current selection and navigation cleanup preserve session lifetime", async ({
  page,
  mockTable,
}) => {
  const view = { ...snapshotFixture("STANDARD"), id: roomId };
  view.cards = [{ ...view.cards[0], zone: "library" }];
  await mockTable.open(view);
  await page.getByRole("button", { name: "library 59", exact: true }).click();
  await expect.poll(() => mockTable.commands.length).toBe(1);
  expect(mockTable.commands[0]).toMatchObject({
    type: "SEARCH",
    data: { target: 0 },
  });
  await page.getByLabel("Search library cards").fill("Front");
  await page.locator(".zone-grid .card").click();
  await expect(
    page.getByRole("dialog", { name: "Card inspector" }),
  ).toBeVisible();
  await update(mockTable, {
    ...view,
    revision: 8,
    cards: view.cards.map((card) => ({ ...card, tapped: true })),
  });
  await expect(
    page.getByRole("dialog", { name: "Card inspector" }),
  ).toContainText("TAPPED");
  await expect(page.getByLabel("Search library cards")).toHaveValue("Front");
  await page.getByRole("button", { name: "Close inspector" }).click();
  await page.getByRole("button", { name: "Close zone viewer" }).click();
  await expect.poll(() => mockTable.commands.length).toBe(2);
  expect(mockTable.commands[1]).toMatchObject({
    type: "CLOSE_SEARCH",
    data: {},
  });
  await update(mockTable, { ...view, revision: 9 });
  await page.getByRole("button", { name: "graveyard 0", exact: true }).click();
  await page.getByRole("button", { name: "Close zone viewer" }).click();
  expect(mockTable.commands.map((command) => command.type)).toEqual([
    "SEARCH",
    "CLOSE_SEARCH",
  ]);
  await page.getByRole("button", { name: "library 59", exact: true }).click();
  await update(mockTable, { ...view, revision: 10 });
  await page.locator(".zone-grid .card").click();
  expect(mockTable.counts()).toEqual({ sessions: 1, connections: 1 });
  await page.evaluate(() => {
    history.pushState({}, "", "/");
    dispatchEvent(new PopStateEvent("popstate"));
  });
  await expect(page.locator("main.welcome")).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.evaluate((room) => {
    history.pushState({}, "", `/room/${room}`);
    dispatchEvent(new PopStateEvent("popstate"));
  }, roomId);
  await expect(page.locator("main.game-layout")).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByText("Connected", { exact: true })).toBeVisible();
  expect(mockTable.counts()).toEqual({ sessions: 1, connections: 2 });
});
