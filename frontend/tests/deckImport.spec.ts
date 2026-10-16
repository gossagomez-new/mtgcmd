import type { Page } from "@playwright/test";
import {
  test,
  expect,
  roomId as room,
  type TableFixture,
} from "./support/tableFixture";
import {
  decklist,
  commander,
  secondCommander,
  previewDeck as previewButton,
  useDeck as loadButton,
} from "./support/selectors";
import { z } from "zod";
import { definitionFixture, snapshotFixture } from "../src/protocolFixtures";
import type { Draft, GameFormat } from "../src/protocol";

const previewBody = z.object({ text: z.string(), url: z.string() });
const loadBody = z.object({
  draftId: z.string(),
  commanders: z.array(z.string()),
  revision: z.number(),
  commandId: z.string().uuid(),
});
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
async function lobby(
  mockTable: TableFixture,
  format: GameFormat = "COMMANDER",
) {
  const page = mockTable.page;
  const view = { ...snapshotFixture(format), id: room, status: "lobby" };
  await mockTable.install(view);
  const previews = await mockTable.captureJson(
    `**/api/rooms/${room}/decks/preview`,
    previewBody,
    () => ({ body: draftFixture() }),
  );
  const loads = await mockTable.captureJson(
    `**/api/rooms/${room}/deck`,
    loadBody,
    () => ({
      body: { ...mockTable.view, revision: mockTable.view.revision + 1 },
    }),
  );
  await page.goto(`/room/${room}`);
  await expect(page.getByText("Connected", { exact: true })).toBeVisible();
  return {
    previews: previews.requests,
    loads: loads.requests,
    previewReplies: previews.replies,
    loadReplies: loads.replies,
    deferPreview: previews.defer,
    deferLoad: loads.defer,
    update: async () => {
      const count = mockTable.view.players[0].deckCount + 1;
      const next = {
        ...mockTable.view,
        revision: mockTable.view.revision + 1,
        players: mockTable.view.players.map((player) => ({
          ...player,
          deckCount: count,
        })),
      };
      await mockTable.snapshot(next, async () => {
        await expect(
          page.getByRole("heading", {
            name: `${count} cards loaded`,
            exact: true,
          }),
        ).toBeVisible();
      });
    },
  };
}
async function preview(page: Page) {
  await previewButton(page).click();
  await expect(page.locator(".deck-preview")).toBeVisible();
  await expect(previewButton(page)).toBeEnabled();
}

test("source inputs preserve raw values, invalidate drafts and survive snapshots", async ({
  page,
  mockTable,
}) => {
  const session = await lobby(mockTable);
  await expect(previewButton(page)).toBeDisabled();
  await decklist(page).fill("  \n ");
  await page.getByLabel("Moxfield URL").fill("  ");
  await expect(previewButton(page)).toBeDisabled();
  const text = "  Commander\n1 Card\n";
  const url = "https://moxfield.com/decks/example ";
  await decklist(page).fill(text);
  await page.getByLabel("Moxfield URL").fill(url);
  await preview(page);
  expect(session.previews).toEqual([{ text, url }]);
  await commander(page).selectOption("c");
  await session.update();
  await expect(commander(page)).toHaveValue("c");
  await expect(decklist(page)).toHaveValue(text);
  await expect(page.getByLabel("Moxfield URL")).toHaveValue(url);
  await decklist(page).fill("changed text");
  await expect(page.locator(".deck-preview")).toHaveCount(0);
  await expect(page.getByLabel("Moxfield URL")).toHaveValue(url);
  await preview(page);
  await expect(commander(page)).toHaveValue("a");
  await page.getByLabel("Moxfield URL").fill("changed URL");
  await expect(page.locator(".deck-preview")).toHaveCount(0);
  await expect(decklist(page)).toHaveValue("changed text");
});

test("commander selections preserve suggestions, secondary rules and exact load arguments", async ({
  page,
  mockTable,
}) => {
  const session = await lobby(mockTable);
  session.previewReplies.push({ body: draftFixture(["a", "b"]) });
  await decklist(page).fill("Commander deck");
  await preview(page);
  const primary = commander(page);
  const secondary = secondCommander(page);
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
  mockTable,
}) => {
  const session = await lobby(mockTable);
  session.previewReplies.push({ body: draftFixture([]) });
  await decklist(page).fill("Deck");
  await preview(page);
  await expect(loadButton(page)).toBeDisabled();
  await commander(page).selectOption("a");
  await expect(loadButton(page)).toBeEnabled();
});

test("Standard hides commander controls and sends an empty commander array", async ({
  page,
  mockTable,
}) => {
  const session = await lobby(mockTable, "STANDARD");
  session.previewReplies.push({ body: draftFixture(["a", "b"]) });
  await decklist(page).fill("60 cards");
  await preview(page);
  await expect(
    page.getByText("100 cards found", { exact: true }),
  ).toBeVisible();
  await expect(commander(page)).toHaveCount(0);
  await expect(secondCommander(page)).toHaveCount(0);
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
  mockTable,
}) => {
  const session = await lobby(mockTable);
  await decklist(page).fill("original source");
  await preview(page);
  const pending = session.deferPreview();
  await previewButton(page).click();
  await expect(
    page.getByRole("button", { name: "Resolving cards…", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByText("100 cards found", { exact: true }),
  ).toBeVisible();
  await expect(loadButton(page)).toBeEnabled();
  await expect(decklist(page)).toBeEditable();
  await expect(page.getByLabel("Moxfield URL")).toBeEditable();
  pending.resolve({ body: { ...draftFixture(["c"]), count: 99 } });
  await expect(page.getByText("99 cards found", { exact: true })).toBeVisible();
  await expect(commander(page)).toHaveValue("c");
  expect(session.previews).toHaveLength(2);
});

test("preview failures preserve drafts and URLs except for the paste fallback", async ({
  page,
  mockTable,
}) => {
  const session = await lobby(mockTable);
  await page
    .getByLabel("Moxfield URL")
    .fill("https://moxfield.com/decks/example");
  await preview(page);
  await commander(page).selectOption("c");
  session.previewReplies.push({
    status: 400,
    body: { message: "Provider unavailable" },
  });
  await previewButton(page).click();
  await expect(page.getByRole("alert")).toContainText("Provider unavailable");
  await expect(previewButton(page)).toBeEnabled();
  await expect(commander(page)).toHaveValue("c");
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
  await expect(commander(page)).toHaveValue("c");
  await expect(previewButton(page)).toBeDisabled();
  await decklist(page).fill("pasted export");
  await preview(page);
  expect(session.previews).toHaveLength(5);
  expect(session.previews[4]).toEqual({ text: "pasted export", url: "" });
});

test("load failures release parent busy state and explicit retries use a fresh command ID", async ({
  page,
  mockTable,
}) => {
  const session = await lobby(mockTable);
  await decklist(page).fill("Deck");
  await preview(page);
  const pending = session.deferLoad();
  await loadButton(page).click();
  await expect.poll(() => session.loads.length).toBe(1);
  await expect(loadButton(page)).toBeDisabled();
  await expect(previewButton(page)).toBeDisabled();
  await expect(decklist(page)).toBeEditable();
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
