import { test, expect, roomId as room } from "./support/tableFixture";
import { z } from "zod";
import { snapshotFixture } from "../src/protocolFixtures";

test("malformed mutation responses show a safe error without retrying", async ({
  page,
  mockTable,
}) => {
  await mockTable.install();
  let posts = 0;
  await page.route("**/api/rooms", (route) => {
    if (route.request().method() === "POST") {
      posts++;
      return route.fulfill({ json: { secret: "PRIVATE-CARD-SECRET" } });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto("/");
  await page.getByLabel("Your name").fill("Alice");
  await page.getByRole("button", { name: "Create table", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Unexpected server response. Refresh before retrying.",
  );
  await expect(page.getByRole("alert")).not.toContainText(
    "PRIVATE-CARD-SECRET",
  );
  await expect(
    page.getByRole("button", { name: "Create table", exact: true }),
  ).toBeEnabled();
  expect(posts).toBe(1);
});

test("initial forbidden room loads stay silent", async ({
  page,
  mockTable,
}) => {
  await mockTable.install();
  await page.route(`**/api/rooms/${room}`, (route) =>
    route.fulfill({ status: 403, json: { message: "Denied" } }),
  );
  await page.goto(`/room/${room}`);
  await expect(
    page.getByRole("button", { name: "Join table", exact: true }),
  ).toBeEnabled();
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("room responses finishing after navigation cannot restore a canceled view", async ({
  page,
  mockTable,
}) => {
  await mockTable.install();
  const roomGet = await mockTable.captureJson(
    `**/api/rooms/${room}`,
    z.null(),
    { body: { ...snapshotFixture(), id: room } },
    "GET",
  );
  const gate = roomGet.defer();
  await page.goto(`/room/${room}`);
  await expect.poll(() => roomGet.requests.length).toBe(1);
  await page.evaluate(() => {
    history.pushState({}, "", "/");
    dispatchEvent(new PopStateEvent("popstate"));
  });
  await expect(
    page.getByRole("button", { name: "Create table", exact: true }),
  ).toBeVisible();
  gate.resolve({ body: { ...snapshotFixture(), id: room } });
  await mockTable.settled();
  await expect(
    page.getByRole("button", { name: "Create table", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: /Next phase/ })).toHaveCount(0);
});

test("malformed socket acknowledgments preserve the view and pending command", async ({
  page,
  mockTable,
}) => {
  const snapshot = { ...snapshotFixture("STANDARD"), id: room };
  await mockTable.open(snapshot);
  const nextPhase = page.getByRole("button", { name: /Next phase/ });
  await expect(nextPhase).toBeEnabled();
  await nextPhase.click();
  await expect.poll(() => mockTable.commands.length).toBe(1);
  await expect(nextPhase).toBeDisabled();
  mockTable.sendRaw(
    JSON.stringify({
      type: "ack",
      commandId: "c",
      view: {
        ...snapshot,
        revision: 8,
        phase: "PRIVATE-CARD-SECRET",
        cards: [{ id: "invalid" }],
      },
    }),
  );
  await expect(page.getByRole("alert")).toContainText(
    "Could not read a server update. Refresh the table.",
  );
  await expect(page.getByText("PRIVATE-CARD-SECRET")).toHaveCount(0);
  await expect(nextPhase).toBeDisabled();
  await mockTable.acknowledge(
    { ...snapshot, revision: 8, phase: "Upkeep" },
    "c",
    async () => {
      await expect(nextPhase).toBeEnabled();
      await expect(page.locator(".turn-strip")).toContainText("Upkeep");
    },
  );
  expect(mockTable.commands.length).toBe(1);
});
