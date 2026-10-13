import { test, expect, type Page } from "@playwright/test";
import type { Snapshot } from "../src/protocol";

test("four guests play with private cards, explicit reveals, and durable reconnect", async ({
  browser,
  request,
}) => {
  test.setTimeout(90000);
  const contexts = await Promise.all(
    Array.from({ length: 4 }, () =>
      browser.newContext({ viewport: { width: 1500, height: 1000 } }),
    ),
  );
  const pages = await Promise.all(contexts.map((c) => c.newPage()));
  const snapshots: (Snapshot | undefined)[] = [];
  const connections = [0, 0, 0, 0];
  const errors: string[] = [];
  pages.forEach((page, i) => {
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("websocket", (socket) => {
      connections[i]++;
      socket.on("framereceived", ({ payload }) => {
        const message = JSON.parse(payload.toString());
        if (message.view) snapshots[i] = message.view;
      });
    });
  });
  try {
    await pages[0].goto("/");
    await pages[0].getByLabel("Your name").fill("Alice");
    await pages[0]
      .getByRole("button", { name: "Create table", exact: true })
      .click();
    await expect(pages[0]).toHaveURL(/\/room\//);
    const roomUrl = pages[0].url();
    for (let i = 1; i < 4; i++) {
      await pages[i].goto(roomUrl);
      await pages[i]
        .getByLabel("Your name")
        .fill(["Alice", "Bob", "Carol", "Dan"][i]);
      await pages[i]
        .getByRole("button", { name: "Join table", exact: true })
        .click();
    }
    for (let i = 0; i < 4; i++)
      await importDeck(pages[i], String.fromCharCode(65 + i));
    await expect(
      pages[0].getByRole("button", { name: "Start game" }),
    ).toBeEnabled();
    await pages[0].getByRole("button", { name: "Start game" }).click();
    for (let i = 0; i < 4; i++) {
      await expect(
        pages[i].getByTestId("hand").getByRole("button", {
          name: `Inspect Secret ${String.fromCharCode(65 + i)}`,
        }),
      ).toHaveCount(7);
      await expect.poll(() => snapshots[i]?.status).toBe("playing");
      await pages[i].evaluate(() => window.scrollTo(0, 0));
      const hand = await pages[i].getByTestId("hand").boundingBox();
      expect(hand!.y + hand!.height).toBeLessThanOrEqual(1000);
      for (let j = 0; j < 4; j++)
        if (j !== i)
          expect(JSON.stringify(snapshots[i])).not.toContain(
            `Secret ${String.fromCharCode(65 + j)}`,
          );
    }
    await pages[0]
      .getByRole("button", { name: "Draw a card", exact: true })
      .click();
    await expect(
      pages[0]
        .getByTestId("hand")
        .getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(8);
    await pages[0]
      .getByTestId("hand")
      .getByRole("button", { name: "Inspect Secret A" })
      .first()
      .click();
    await pages[0]
      .getByRole("button", { name: "Play to battlefield", exact: true })
      .click();
    await expect(
      pages[0]
        .getByTestId("your-battlefield")
        .getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(1);
    await expect(
      pages[1]
        .getByTestId("opponent-0")
        .getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(1);
    await pages[0]
      .getByRole("button", { name: "Lose 1 life", exact: true })
      .click();
    await expect(pages[0].getByTestId("your-life")).toHaveText("39");
    await pages[0]
      .getByTestId("hand")
      .getByRole("button", { name: "Inspect Secret A" })
      .first()
      .click();
    await pages[0]
      .getByRole("button", { name: "Reveal to table", exact: true })
      .click();
    await expect
      .poll(
        () =>
          snapshots[1]?.cards.filter((c) => c.owner === 0 && c.zone === "hand")
            .length,
      )
      .toBe(1);
    await pages[0].getByRole("button", { name: "Close inspector" }).click();
    await pages[0].screenshot({
      path: "test-results/tabletop.png",
      fullPage: true,
    });
    await pages[0].reload();
    await expect(pages[0].getByTestId("your-life")).toHaveText("39");
    await expect(
      pages[0]
        .getByTestId("hand")
        .getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(7);
    const before = snapshots[0]?.revision;
    const previousConnections = connections[0];
    await request.post("/__test/restart");
    await expect
      .poll(() => connections[0], { timeout: 30000 })
      .toBeGreaterThan(previousConnections);
    await expect
      .poll(() => snapshots[0]?.revision, { timeout: 30000 })
      .toBe(before);
    await expect(pages[0].getByText("Connected", { exact: true })).toBeVisible({
      timeout: 30000,
    });
    await pages[0]
      .getByRole("button", { name: "Draw a card", exact: true })
      .click();
    await expect(
      pages[0]
        .getByTestId("hand")
        .getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(8);
    await pages[0].getByText("Share private zones", { exact: true }).click();
    await pages[0].getByLabel("Bob", { exact: true }).click();
    await expect(pages[0].getByLabel("Bob", { exact: true })).toBeChecked();
    await expect.poll(() => snapshots[1]?.players[0].privateAccess).toBe(true);
    await pages[1]
      .getByTestId("opponent-0")
      .getByRole("button", { name: "Library 11", exact: true })
      .click();
    await expect(
      pages[1]
        .getByRole("dialog", { name: "Zone viewer" })
        .getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(11);
    expect(
      snapshots[2]?.cards.filter((c) => c.owner === 0 && c.zone === "library"),
    ).toHaveLength(0);
    await pages[0].getByLabel("Bob", { exact: true }).click();
    await expect(pages[0].getByLabel("Bob", { exact: true })).not.toBeChecked();
    await expect
      .poll(
        () =>
          snapshots[1]?.cards.filter(
            (c) => c.owner === 0 && c.zone === "library",
          ).length,
      )
      .toBe(0);
    await pages[1].getByRole("button", { name: "Close zone viewer" }).click();
    await pages[0]
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect Secret A" })
      .click();
    await pages[0]
      .getByRole("button", { name: "Exile face down", exact: true })
      .click();
    await expect
      .poll(
        () =>
          snapshots[1]?.cards.filter(
            (c) =>
              c.owner === 0 &&
              c.zone === "exile" &&
              c.faceDown &&
              !c.definition,
          ).length,
      )
      .toBe(1);
    expect(errors).toEqual([]);
  } finally {
    await Promise.all(contexts.map((c) => c.close()));
  }
});
test("failed URL import offers a usable pasted-deck fallback", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByLabel("Your name").fill("Importer");
  await page.getByRole("button", { name: "Create table", exact: true }).click();
  await expect(page).toHaveURL(/\/room\//);
  await page.route("**/api/rooms/*/decks/preview", async (route) => {
    const body = route.request().postDataJSON();
    if (body.url)
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({
          message:
            "Moxfield is unavailable. Export the deck and paste its text instead.",
        }),
      });
    else await route.continue();
  });
  await page
    .getByLabel("Moxfield URL")
    .fill("https://moxfield.com/decks/example");
  await page.getByRole("button", { name: "Preview deck", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("paste");
  await expect(page.getByLabel("Moxfield URL")).toBeEmpty();
  await importDeck(page, "A");
});

async function importDeck(page: Page, letter: string) {
  await page
    .getByLabel("Decklist")
    .fill(`Commander\n1 Demo Commander\nDeck\n20 Secret ${letter}`);
  await page.getByRole("button", { name: "Preview deck", exact: true }).click();
  await page
    .getByRole("button", { name: "Use this deck", exact: true })
    .click();
  await page.getByLabel("Accept deck warnings").check();
  await page
    .getByRole("button", { name: "Ready to play", exact: true })
    .click();
  await expect(page.getByText("You are ready", { exact: true })).toBeVisible();
}
