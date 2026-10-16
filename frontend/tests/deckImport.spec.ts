import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";
import { z } from "zod";
import { definitionFixture, snapshotFixture } from "../src/protocolFixtures";
import type { Draft, GameFormat } from "../src/protocol";

const room = "00000000-0000-4000-8000-000000000001";
const previewBody = z.object({ text: z.string(), url: z.string() });
const loadBody = z.object({
  draftId: z.string(),
  commanders: z.array(z.string()),
  revision: z.number(),
  commandId: z.string().uuid(),
});
type Reply = { status?: number; body: unknown };
function draftFixture(suggested = ["a"]): Draft {
  return {
    draftId: "draft",
    count: 100,
    suggested,
    choices: ["a", "b", "c"].map((id) => ({
      ...definitionFixture(),
      id,
      name: `Commander ${id}`,
    })),
  };
}
function deferred() {
  let resolve!: (reply: Reply) => void;
  const promise = new Promise<Reply>((finish) => {
    resolve = finish;
  });
  return { promise, resolve };
}
async function lobby(page: Page, format: GameFormat = "COMMANDER") {
  let view = { ...snapshotFixture(format), id: room, status: "lobby" };
  let socket: WebSocketRoute;
  const previewReplies: (Reply | Promise<Reply>)[] = [];
  const loadReplies: (Reply | Promise<Reply>)[] = [];
  const previews: z.infer<typeof previewBody>[] = [];
  const loads: z.infer<typeof loadBody>[] = [];
  await page.route("**/api/session", (route) =>
    route.fulfill({ json: { csrf: "token" } }),
  );
  await page.route("**/api/rooms", (route) => route.fulfill({ json: [] }));
  await page.route(`**/api/rooms/${room}`, (route) =>
    route.fulfill({ json: view }),
  );
  await page.route(`**/api/rooms/${room}/decks/preview`, async (route) => {
    previews.push(previewBody.parse(route.request().postDataJSON()));
    const reply = await (previewReplies.shift() ?? { body: draftFixture() });
    await route.fulfill({ status: reply.status ?? 200, json: reply.body });
  });
  await page.route(`**/api/rooms/${room}/deck`, async (route) => {
    loads.push(loadBody.parse(route.request().postDataJSON()));
    const reply = await (loadReplies.shift() ?? {
      body: { ...view, revision: view.revision + 1 },
    });
    await route.fulfill({ status: reply.status ?? 200, json: reply.body });
  });
  await page.routeWebSocket("**/ws?room=**", (route) => {
    socket = route;
    route.onMessage(() => {});
  });
  await page.goto(`/room/${room}`);
  await expect(page.getByText("Connected", { exact: true })).toBeVisible();
  return {
    previews,
    loads,
    previewReplies,
    loadReplies,
    update: async () => {
      const count = view.players[0].deckCount + 1;
      view = {
        ...view,
        revision: view.revision + 1,
        players: view.players.map((player) => ({
          ...player,
          deckCount: count,
        })),
      };
      socket.send(JSON.stringify({ type: "snapshot", view }));
      await expect(
        page.getByRole("heading", {
          name: `${count} cards loaded`,
          exact: true,
        }),
      ).toBeVisible();
    },
  };
}
const previewButton = (page: Page) =>
  page.getByRole("button", { name: "Preview deck", exact: true });
const loadButton = (page: Page) =>
  page.getByRole("button", { name: "Use this deck", exact: true });
async function preview(page: Page) {
  await previewButton(page).click();
  await expect(page.locator(".deck-preview")).toBeVisible();
  await expect(previewButton(page)).toBeEnabled();
}

test("source inputs preserve raw values, invalidate drafts and survive snapshots", async ({
  page,
}) => {
  const session = await lobby(page);
  await expect(previewButton(page)).toBeDisabled();
  await page.getByLabel(/^Decklist/).fill("  \n ");
  await page.getByLabel("Moxfield URL").fill("  ");
  await expect(previewButton(page)).toBeDisabled();
  const text = "  Commander\n1 Card\n";
  const url = "https://moxfield.com/decks/example ";
  await page.getByLabel(/^Decklist/).fill(text);
  await page.getByLabel("Moxfield URL").fill(url);
  await preview(page);
  expect(session.previews).toEqual([{ text, url }]);
  await page.getByLabel(/^Commander/).selectOption("c");
  await session.update();
  await expect(page.getByLabel(/^Commander/)).toHaveValue("c");
  await expect(page.getByLabel(/^Decklist/)).toHaveValue(text);
  await expect(page.getByLabel("Moxfield URL")).toHaveValue(url);
  await page.getByLabel(/^Decklist/).fill("changed text");
  await expect(page.locator(".deck-preview")).toHaveCount(0);
  await expect(page.getByLabel("Moxfield URL")).toHaveValue(url);
  await preview(page);
  await expect(page.getByLabel(/^Commander/)).toHaveValue("a");
  await page.getByLabel("Moxfield URL").fill("changed URL");
  await expect(page.locator(".deck-preview")).toHaveCount(0);
  await expect(page.getByLabel(/^Decklist/)).toHaveValue("changed text");
});

test("commander selections preserve suggestions, secondary rules and exact load arguments", async ({
  page,
}) => {
  const session = await lobby(page);
  session.previewReplies.push({ body: draftFixture(["a", "b"]) });
  await page.getByLabel(/^Decklist/).fill("Commander deck");
  await preview(page);
  const primary = page.getByLabel(/^Commander/);
  const secondary = page.getByLabel(/^Second commander/);
  await expect(primary).toHaveValue("a");
  await expect(secondary).toHaveValue("b");
  await expect(secondary.locator("option[value=a]")).toHaveCount(0);
  await primary.selectOption("c");
  await expect(secondary).toHaveValue("b");
  await session.update();
  await expect(primary).toHaveValue("c");
  await expect(secondary).toHaveValue("b");
  await loadButton(page).click();
  await expect.poll(() => session.loads.length).toBe(1);
  expect(session.loads[0]).toEqual({
    draftId: "draft",
    commanders: ["c", "b"],
    revision: 8,
    commandId: expect.any(String),
  });
  await expect(loadButton(page)).toBeEnabled();
  await secondary.selectOption("");
  await expect(secondary).toHaveValue("");
  await primary.selectOption("");
  await expect(loadButton(page)).toBeDisabled();
  await secondary.selectOption("b");
  // Preserve the current compacting behavior when a second choice is made without a primary.
  await expect(primary).toHaveValue("b");
  await expect(secondary).toHaveValue("");
});

test("empty commander suggestions require selection before loading", async ({
  page,
}) => {
  const session = await lobby(page);
  session.previewReplies.push({ body: draftFixture([]) });
  await page.getByLabel(/^Decklist/).fill("Deck");
  await preview(page);
  await expect(loadButton(page)).toBeDisabled();
  await page.getByLabel(/^Commander/).selectOption("a");
  await expect(loadButton(page)).toBeEnabled();
});

test("Standard hides commander controls and sends an empty commander array", async ({
  page,
}) => {
  const session = await lobby(page, "STANDARD");
  session.previewReplies.push({ body: draftFixture(["a", "b"]) });
  await page.getByLabel(/^Decklist/).fill("60 cards");
  await preview(page);
  await expect(
    page.getByText("100 cards found", { exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel(/^Commander/)).toHaveCount(0);
  await expect(page.getByLabel(/^Second commander/)).toHaveCount(0);
  await loadButton(page).click();
  await expect.poll(() => session.loads.length).toBe(1);
  expect(session.loads[0]).toEqual({
    draftId: "draft",
    commanders: [],
    revision: 7,
    commandId: expect.any(String),
  });
});

test("pending re-preview retains the existing draft and keeps source fields editable", async ({
  page,
}) => {
  const session = await lobby(page);
  await page.getByLabel(/^Decklist/).fill("original source");
  await preview(page);
  const pending = deferred();
  session.previewReplies.push(pending.promise);
  await previewButton(page).click();
  await expect(
    page.getByRole("button", { name: "Resolving cards…", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByText("100 cards found", { exact: true }),
  ).toBeVisible();
  await expect(loadButton(page)).toBeEnabled();
  await expect(page.getByLabel(/^Decklist/)).toBeEditable();
  await expect(page.getByLabel("Moxfield URL")).toBeEditable();
  pending.resolve({ body: { ...draftFixture(["c"]), count: 99 } });
  await expect(page.getByText("99 cards found", { exact: true })).toBeVisible();
  await expect(page.getByLabel(/^Commander/)).toHaveValue("c");
  expect(session.previews).toHaveLength(2);
});

test("preview failures preserve drafts and URLs except for the paste fallback", async ({
  page,
}) => {
  const session = await lobby(page);
  await page
    .getByLabel("Moxfield URL")
    .fill("https://moxfield.com/decks/example");
  await preview(page);
  await page.getByLabel(/^Commander/).selectOption("c");
  session.previewReplies.push({
    status: 400,
    body: { message: "Provider unavailable" },
  });
  await previewButton(page).click();
  await expect(page.getByRole("alert")).toContainText("Provider unavailable");
  await expect(previewButton(page)).toBeEnabled();
  await expect(page.getByLabel(/^Commander/)).toHaveValue("c");
  await expect(page.getByLabel("Moxfield URL")).toHaveValue(
    "https://moxfield.com/decks/example",
  );
  session.previewReplies.push({ body: { unexpected: "payload" } });
  await previewButton(page).click();
  await expect(page.getByRole("alert")).toContainText(
    "Unexpected server response",
  );
  await expect(previewButton(page)).toBeEnabled();
  session.previewReplies.push({
    status: 400,
    body: { message: "Please PASTE an export instead" },
  });
  await previewButton(page).click();
  await expect(page.getByRole("alert")).toContainText("Please PASTE");
  await expect(page.getByLabel("Moxfield URL")).toHaveValue("");
  await expect(page.getByLabel(/^Commander/)).toHaveValue("c");
  await expect(previewButton(page)).toBeDisabled();
  await page.getByLabel(/^Decklist/).fill("pasted export");
  await preview(page);
  expect(session.previews).toHaveLength(5);
  expect(session.previews[4]).toEqual({ text: "pasted export", url: "" });
});

test("load failures release parent busy state and explicit retries use a fresh command ID", async ({
  page,
}) => {
  const session = await lobby(page);
  await page.getByLabel(/^Decklist/).fill("Deck");
  await preview(page);
  const pending = deferred();
  session.loadReplies.push(pending.promise);
  await loadButton(page).click();
  await expect.poll(() => session.loads.length).toBe(1);
  await expect(loadButton(page)).toBeDisabled();
  await expect(previewButton(page)).toBeDisabled();
  await expect(page.getByLabel(/^Decklist/)).toBeEditable();
  pending.resolve({
    status: 400,
    body: { message: "Choose a valid commander" },
  });
  await expect(page.getByRole("alert")).toContainText(
    "Choose a valid commander",
  );
  await expect(loadButton(page)).toBeEnabled();
  await expect(previewButton(page)).toBeEnabled();
  expect(session.loads).toHaveLength(1);
  await loadButton(page).click();
  await expect.poll(() => session.loads.length).toBe(2);
  await expect(loadButton(page)).toBeEnabled();
  expect(session.loads[1].commandId).not.toBe(session.loads[0].commandId);
  expect(session.loads[1].commanders).toEqual(["a"]);
});
