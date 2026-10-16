import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";
import { snapshotFixture } from "../src/protocolFixtures";
import type { Snapshot } from "../src/protocol";

const room = "00000000-0000-4000-8000-000000000001";
async function table(page: Page, snapshot: Snapshot) {
  let sessions = 0;
  let connections = 0;
  let socket: WebSocketRoute;
  await page.route("**/api/session", (route) => {
    sessions++;
    return route.fulfill({ json: { csrf: "token" } });
  });
  await page.route("**/api/rooms", (route) => route.fulfill({ json: [] }));
  await page.route(`**/api/rooms/${room}`, (route) =>
    route.fulfill({ json: snapshot }),
  );
  await page.routeWebSocket("**/ws?room=**", (route) => {
    connections++;
    socket = route;
    route.onMessage(() => {});
  });
  await page.goto(`/room/${room}`);
  await expect(page.getByText("Connected", { exact: true })).toBeVisible();
  return {
    update(view: Snapshot) {
      socket.send(JSON.stringify({ type: "ack", commandId: "test", view }));
    },
    counts() {
      return { sessions, connections };
    },
  };
}

test("warning acceptance follows deck revision and starting player survives updates", async ({
  page,
}) => {
  const view = { ...snapshotFixture("STANDARD"), id: room, status: "lobby" };
  view.players[0] = {
    ...view.players[0],
    ready: false,
    warnings: ["Check this deck"],
  };
  view.players.push({ ...view.players[0], seat: 1, name: "Bob" });
  const session = await table(page, view);
  await page.getByLabel("Starting player").selectOption("1");
  await page.getByLabel("Accept deck warnings").check();
  session.update({ ...view, revision: 8 });
  await expect(page.getByLabel("Accept deck warnings")).toBeChecked();
  await expect(page.getByLabel("Starting player")).toHaveValue("1");
  session.update({
    ...view,
    revision: 9,
    players: view.players.map((p) => ({ ...p, deckRevision: 2 })),
  });
  await expect(page.getByLabel("Accept deck warnings")).not.toBeChecked();
  await expect(
    page.getByRole("button", { name: "Ready to play", exact: true }),
  ).toBeDisabled();
  expect(session.counts()).toEqual({ sessions: 1, connections: 1 });
});

test("conceded host can finish while game actions remain disabled", async ({
  page,
}) => {
  const view = { ...snapshotFixture("STANDARD"), id: room };
  view.players[0].conceded = true;
  const session = await table(page, view);
  await expect(
    page.getByRole("button", { name: "Draw a card", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Finish game", exact: true }),
  ).toBeEnabled();
  session.update({ ...view, revision: 8, status: "finished" });
  await expect(
    page.getByRole("button", { name: "Finish game", exact: true }),
  ).toBeDisabled();
  await expect(page.locator(".finished-banner")).toBeVisible();
});

test("stack interleaves spells and abilities in descending order", async ({
  page,
}) => {
  const view = { ...snapshotFixture("STANDARD"), id: room };
  view.cards = [{ ...view.cards[0], zone: "stack", stackOrder: 3 }];
  view.abilities.push({
    id: "newest",
    controller: 0,
    text: "Newest ability",
    stackOrder: 4,
  });
  const session = await table(page, view);
  const items = page.locator(".stack-items > div");
  await expect(items).toHaveCount(3);
  await expect(items.nth(0)).toContainText("Newest ability");
  await expect(items.nth(1)).toContainText("Front");
  await expect(items.nth(2)).toContainText("Ability");
  session.update({ ...view, revision: 8, cards: [], abilities: [] });
  await expect(page.locator(".stack-items")).toContainText(
    "Spells and abilities meet here",
  );
});

test("library filter and inspected card follow their intended snapshot lifetimes", async ({
  page,
}) => {
  const view = { ...snapshotFixture("STANDARD"), id: room };
  view.cards = [{ ...view.cards[0], zone: "library" }];
  const session = await table(page, view);
  await page.getByRole("button", { name: "library 59", exact: true }).click();
  await page.getByLabel("Search library cards").fill("Private");
  session.update({ ...view, revision: 8 });
  await expect(page.getByLabel("Search library cards")).toHaveValue("Private");
  await page.locator(".zone-grid .card").click();
  await expect(
    page.getByRole("dialog", { name: "Card inspector" }),
  ).toBeVisible();
  const changed = {
    ...view,
    revision: 9,
    cards: view.cards.map((card) => ({ ...card, tapped: true })),
  };
  session.update(changed);
  await expect(
    page.getByRole("dialog", { name: "Card inspector" }),
  ).toContainText("TAPPED");
  session.update({ ...changed, revision: 10, cards: [] });
  await expect(
    page.getByRole("dialog", { name: "Card inspector" }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Close zone viewer" }).click();
  session.update({ ...view, revision: 11 });
  await page.getByRole("button", { name: "library 59", exact: true }).click();
  await expect(page.getByLabel("Search library cards")).toHaveValue("");
  expect(session.counts()).toEqual({ sessions: 1, connections: 1 });
});

test("welcome form values survive a visit to a table", async ({ page }) => {
  const view = { ...snapshotFixture("STANDARD"), id: room };
  await page.route("**/api/session", (route) =>
    route.fulfill({ json: { csrf: "token" } }),
  );
  await page.route("**/api/rooms", (route) =>
    route.fulfill({ json: route.request().method() === "POST" ? view : [] }),
  );
  await page.route(`**/api/rooms/${room}`, (route) =>
    route.fulfill({ json: view }),
  );
  await page.routeWebSocket("**/ws?room=**", (route) =>
    route.onMessage(() => {}),
  );
  await page.goto("/");
  await page.getByLabel("Your name").fill("Persistent name");
  await page.getByLabel("Table format").selectOption("STANDARD");
  await page.getByRole("button", { name: "Create table", exact: true }).click();
  await expect(page.getByText("Connected", { exact: true })).toBeVisible();
  await page.evaluate(() => {
    history.pushState({}, "", "/");
    dispatchEvent(new PopStateEvent("popstate"));
  });
  await expect(page.getByLabel("Your name")).toHaveValue("Persistent name");
  await expect(page.getByLabel("Table format")).toHaveValue("STANDARD");
});
