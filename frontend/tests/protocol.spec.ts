import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";
import { snapshotFixture } from "../src/protocolFixtures";

const room = "00000000-0000-4000-8000-000000000001";

async function bootstrap(page: Page) {
  await page.route("**/api/session", (route) =>
    route.fulfill({ json: { csrf: "test-token" } }),
  );
  await page.route("**/api/rooms", (route) => route.fulfill({ json: [] }));
}

test("malformed mutation responses show a safe error without retrying", async ({
  page,
}) => {
  await bootstrap(page);
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

test("initial forbidden room loads stay silent", async ({ page }) => {
  await bootstrap(page);
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
}) => {
  await bootstrap(page);
  let release = () => {};
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  let requested = false;
  let finished = false;
  await page.route(`**/api/rooms/${room}`, async (route) => {
    requested = true;
    await gate;
    await route.fulfill({ json: { ...snapshotFixture(), id: room } });
    finished = true;
  });
  await page.goto(`/room/${room}`);
  await expect.poll(() => requested).toBe(true);
  await page.evaluate(() => {
    history.pushState({}, "", "/");
    dispatchEvent(new PopStateEvent("popstate"));
  });
  await expect(
    page.getByRole("button", { name: "Create table", exact: true }),
  ).toBeVisible();
  release();
  await expect.poll(() => finished).toBe(true);
  await expect(
    page.getByRole("button", { name: "Create table", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: /Next phase/ })).toHaveCount(0);
});

test("malformed socket acknowledgments preserve the view and pending command", async ({
  page,
}) => {
  await bootstrap(page);
  const snapshot = { ...snapshotFixture("STANDARD"), id: room };
  await page.route(`**/api/rooms/${room}`, (route) =>
    route.fulfill({ json: snapshot }),
  );
  let socket: WebSocketRoute | undefined;
  let commands = 0;
  await page.routeWebSocket("**/ws?room=**", (route) => {
    socket = route;
    route.onMessage(() => {
      commands++;
    });
  });
  await page.goto(`/room/${room}`);
  const nextPhase = page.getByRole("button", { name: /Next phase/ });
  await expect(nextPhase).toBeEnabled();
  await nextPhase.click();
  await expect.poll(() => commands).toBe(1);
  await expect(nextPhase).toBeDisabled();
  socket!.send(
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
  socket!.send(
    JSON.stringify({
      type: "ack",
      commandId: "c",
      view: { ...snapshot, revision: 8, phase: "Upkeep" },
    }),
  );
  await expect(nextPhase).toBeEnabled();
  await expect(page.locator(".turn-strip")).toContainText("Upkeep");
  expect(commands).toBe(1);
});
