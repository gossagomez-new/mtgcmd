import { test, expect, type Page, type Locator } from "@playwright/test";
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
    const ownPermanent = pages[0]
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect Secret A" });
    const opponentPermanent = pages[1]
      .getByTestId("opponent-0")
      .getByRole("button", { name: "Inspect Secret A" });
    await expectRotation(ownPermanent, 0);
    await ownPermanent.click();
    await pages[0].getByRole("button", { name: "Tap", exact: true }).click();
    await expectRotation(ownPermanent, 90);
    await expectRotation(opponentPermanent, 90);
    await ownPermanent.hover();
    await expectRotation(ownPermanent, 90);
    await opponentPermanent.hover();
    await expectRotation(opponentPermanent, 90);
    await pages[0].getByRole("button", { name: "Untap", exact: true }).click();
    await expectRotation(ownPermanent, 0);
    await expectRotation(opponentPermanent, 0);
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
    await pages[1]
      .getByRole("searchbox", { name: "Search library cards" })
      .fill("Secret A");
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
    await expect(
      pages[1]
        .getByRole("dialog", { name: "Zone viewer" })
        .getByRole("button", { name: /^Inspect / }),
    ).toHaveCount(0);
    await expect(
      pages[1].getByText("No visible cards in this zone."),
    ).toBeVisible();
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

async function expectRotation(card: Locator, degrees: number) {
  await expect
    .poll(() =>
      card.evaluate((element) => {
        const matrix = new DOMMatrixReadOnly(
          getComputedStyle(element).transform,
        );
        return Math.round((Math.atan2(matrix.b, matrix.a) * 180) / Math.PI);
      }),
    )
    .toBe(degrees);
}

test("private library search filters locally and retains library positions", async ({
  browser,
}) => {
  const contexts = await Promise.all(
    Array.from({ length: 4 }, () => browser.newContext()),
  );
  const pages = await Promise.all(contexts.map((context) => context.newPage()));
  try {
    await pages[0].goto("/");
    await pages[0].getByLabel("Your name").fill("Search host");
    await pages[0]
      .getByRole("button", { name: "Create table", exact: true })
      .click();
    await expect(pages[0]).toHaveURL(/\/room\//);
    const roomUrl = pages[0].url();
    for (let i = 1; i < 4; i++) {
      await pages[i].goto(roomUrl);
      await pages[i].getByLabel("Your name").fill(`Search player ${i}`);
      await pages[i]
        .getByRole("button", { name: "Join table", exact: true })
        .click();
    }
    for (let i = 0; i < 4; i++) {
      await pages[i]
        .getByLabel("Decklist")
        .fill("Commander\n1 Demo Commander\nDeck\n10 Secret A\n10 Secret B");
      await pages[i]
        .getByRole("button", { name: "Preview deck", exact: true })
        .click();
      await pages[i]
        .getByRole("button", { name: "Use this deck", exact: true })
        .click();
      await pages[i].getByLabel("Accept deck warnings").check();
      await pages[i]
        .getByRole("button", { name: "Ready to play", exact: true })
        .click();
      await expect(
        pages[i].getByText("You are ready", { exact: true }),
      ).toBeVisible();
    }
    await pages[0]
      .getByRole("button", { name: "Start game", exact: true })
      .click();
    const page = pages[0];
    await page
      .locator(".zone-shortcuts")
      .getByRole("button", { name: "library 13", exact: true })
      .click();
    const modal = page.getByRole("dialog", { name: "Zone viewer" });
    await expect(modal.getByRole("button", { name: /^Inspect / })).toHaveCount(
      13,
    );
    const apiUrl = new URL(
      `/api/rooms/${new URL(roomUrl).pathname.split("/").pop()}`,
      roomUrl,
    ).toString();
    const before = (await (await page.request.get(apiUrl)).json()) as Snapshot;
    const library = before.cards.filter(
      (card) => card.zone === "library" && card.zoneOwner === before.you,
    );
    const matching = library
      .map((card, index) => ({ card, position: index + 1 }))
      .filter((entry) => entry.card.definition?.name === "Secret A");
    const input = modal.getByRole("searchbox", {
      name: "Search library cards",
    });
    await input.fill("  sEcReT a  ");
    await expect(
      modal.getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(matching.length);
    await expect(
      modal.getByRole("button", { name: "Inspect Secret B" }),
    ).toHaveCount(0);
    await expect(modal.locator(".zone-grid > div > small")).toHaveText(
      matching.map((entry) => `#${entry.position}`),
    );
    await modal
      .getByRole("button", { name: "Inspect Secret A" })
      .first()
      .click();
    await expect(
      page
        .getByRole("dialog", { name: "Card inspector" })
        .getByRole("heading", { name: "Secret A", exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Close inspector" }).click();
    await input.fill("not-a-card");
    await expect(modal.getByRole("button", { name: /^Inspect / })).toHaveCount(
      0,
    );
    await expect(modal.getByText("No cards match your search.")).toBeVisible();
    await modal
      .getByRole("button", { name: "Clear search", exact: true })
      .click();
    await expect(input).toBeEmpty();
    await expect(modal.getByRole("button", { name: /^Inspect / })).toHaveCount(
      13,
    );
    const after = (await (await page.request.get(apiUrl)).json()) as Snapshot;
    expect(after.revision).toBe(before.revision);
    expect(after.cards).toEqual(before.cards);
    const otherView = (await (
      await pages[1].request.get(apiUrl)
    ).json()) as Snapshot;
    expect(
      otherView.cards.filter(
        (card) => card.zone === "library" && card.zoneOwner === before.you,
      ),
    ).toHaveLength(0);
    await input.fill("Secret B");
    await page.getByRole("button", { name: "Close zone viewer" }).click();
    await page
      .locator(".zone-shortcuts")
      .getByRole("button", { name: "library 13", exact: true })
      .click();
    await expect(input).toBeEmpty();
    await expect(modal.getByRole("button", { name: /^Inspect / })).toHaveCount(
      13,
    );
    await page.getByRole("button", { name: "Close zone viewer" }).click();
    await page
      .locator(".zone-shortcuts")
      .getByRole("button", { name: "graveyard 0", exact: true })
      .click();
    await expect(modal.getByRole("searchbox")).toHaveCount(0);
    await expect(
      modal.getByText("No visible cards in this zone."),
    ).toBeVisible();
  } finally {
    await Promise.all(contexts.map((context) => context.close()));
  }
});

test("battlefields group types automatically and place lands below other permanents", async ({
  browser,
}) => {
  const contexts = await Promise.all(
    Array.from({ length: 4 }, () => browser.newContext()),
  );
  const pages = await Promise.all(contexts.map((context) => context.newPage()));
  try {
    await pages[0].goto("/");
    await pages[0].getByLabel("Your name").fill("Grouping host");
    await pages[0]
      .getByRole("button", { name: "Create table", exact: true })
      .click();
    await expect(pages[0]).toHaveURL(/\/room\//);
    const roomUrl = pages[0].url();
    for (let i = 1; i < 4; i++) {
      await pages[i].goto(roomUrl);
      await pages[i].getByLabel("Your name").fill(`Grouping player ${i}`);
      await pages[i]
        .getByRole("button", { name: "Join table", exact: true })
        .click();
    }
    for (let i = 0; i < 4; i++)
      await importDeck(pages[i], String.fromCharCode(65 + i));
    await pages[0]
      .getByRole("button", { name: "Start game", exact: true })
      .click();
    const page = pages[0];
    const field = page.getByTestId("your-battlefield");
    const otherField = pages[1]
      .getByTestId("opponent-0")
      .locator(".opponent-field");
    await expect(
      page
        .getByTestId("hand")
        .getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(7);
    const apiUrl = new URL(
      `/api/rooms/${new URL(roomUrl).pathname.split("/").pop()}`,
      roomUrl,
    ).toString();
    const { csrf } = await (
      await page.request.get(new URL("/api/session", roomUrl).toString())
    ).json();
    async function command(type: string, data: Record<string, unknown>) {
      const snapshot = (await (
        await page.request.get(apiUrl)
      ).json()) as Snapshot;
      const response = await page.request.post(`${apiUrl}/commands`, {
        headers: { "X-CSRF-Token": csrf, Origin: new URL(roomUrl).origin },
        data: {
          id: crypto.randomUUID(),
          revision: snapshot.revision,
          type,
          data,
        },
      });
      const result = await response.json();
      expect(response.ok(), JSON.stringify(result)).toBeTruthy();
      return result as Snapshot;
    }
    await command("TOKEN", {
      name: "First creature",
      type: "Creature — Elf",
      text: "1/1",
    });
    await command("TOKEN", {
      name: "Middle artifact",
      type: "Artifact",
      text: "",
    });
    const state = await command("TOKEN", {
      name: "Second creature",
      type: "Artifact Creature — Golem",
      text: "2/2",
    });
    const expected = [
      "Inspect First creature",
      "Inspect Second creature",
      "Inspect Middle artifact",
    ];
    for (const root of [field, otherField]) {
      await expect
        .poll(() =>
          root
            .locator(".card")
            .evaluateAll((elements) =>
              elements.map((element) => element.getAttribute("aria-label")),
            ),
        )
        .toEqual(expected);
    }
    const first = state.cards.find(
      (card) => card.definition?.name === "First creature",
    )!;
    await command("POSITION", { cardId: first.id, x: 0.9, y: 0.9 });
    await expect(field.locator(".positioned-card")).toHaveCount(0);
    const creatureGroup = field.getByRole("group", {
      name: "Creatures",
      exact: true,
    });
    await expect(creatureGroup.getByRole("button")).toHaveCount(2);
    const handCard = page
      .getByTestId("hand")
      .getByRole("button", { name: "Inspect Secret A" })
      .first();
    const transfer = await page.evaluateHandle(() => new DataTransfer());
    await handCard.dispatchEvent("dragstart", { dataTransfer: transfer });
    await field.dispatchEvent("drop", { dataTransfer: transfer });
    const landGroup = field.getByRole("group", { name: "Lands", exact: true });
    await expect(
      landGroup.getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(1);
    await expect(
      otherField
        .getByRole("group", { name: "Lands", exact: true })
        .getByRole("button", { name: "Inspect Secret A" }),
    ).toHaveCount(1);
    for (const root of [field, otherField]) {
      const lands = await root
        .getByRole("group", { name: "Lands", exact: true })
        .boundingBox();
      const artifacts = await root
        .getByRole("group", { name: "Artifacts", exact: true })
        .boundingBox();
      expect(lands!.y).toBeGreaterThanOrEqual(artifacts!.y + artifacts!.height);
    }
    await expect(creatureGroup.getByRole("button").first()).toHaveAttribute(
      "draggable",
      "false",
    );
    await creatureGroup
      .getByRole("button", { name: "Inspect First creature" })
      .click();
    await page.getByRole("button", { name: "Tap", exact: true }).click();
    const tapped = creatureGroup.getByRole("button", {
      name: "Inspect First creature",
    });
    await tapped.hover();
    await expectRotation(tapped, 90);
    await expectRotation(
      otherField.getByRole("button", { name: "Inspect First creature" }),
      90,
    );
    await page.getByRole("button", { name: "Untap", exact: true }).click();
    await expectRotation(tapped, 0);
    await page.getByRole("button", { name: "Close inspector" }).click();
    await page.screenshot({
      path: "test-results/battlefield-grouping.png",
      fullPage: true,
    });
  } finally {
    await Promise.all(contexts.map((context) => context.close()));
  }
});
