import { test, expect, roomId } from "./support/tableFixture";
import { snapshotFixture } from "../src/protocolFixtures";
import type { GameFormat } from "../src/protocol";

for (const format of ["COMMANDER", "STANDARD"] satisfies GameFormat[]) {
  test(`${format}: zone viewer contains keyboard focus and restores its trigger`, async ({
    page,
    mockTable,
  }) => {
    const view = { ...snapshotFixture(format), id: roomId };
    view.cards = [{ ...view.cards[0], zone: "graveyard" }];
    view.players[0].counts.graveyard = 1;
    await mockTable.open(view);
    const trigger = page.getByRole("button", {
      name: "graveyard 1",
      exact: true,
    });
    await trigger.click();
    const viewer = page.getByRole("dialog", { name: "Zone viewer" });
    await expect(viewer).toBeVisible();
    await expect(
      viewer.getByRole("button", { name: "Close zone viewer" }),
    ).toBeFocused();
    expect(await viewer.evaluate((element) => element.matches(":modal"))).toBe(
      true,
    );
    await page.locator(".draw-controls button").first().focus();
    await expect(
      viewer.getByRole("button", { name: "Close zone viewer" }),
    ).toBeFocused();
    for (const key of [
      "Tab",
      "Tab",
      "Tab",
      "Shift+Tab",
      "Shift+Tab",
      "Shift+Tab",
    ]) {
      await page.keyboard.press(key);
      expect(
        await viewer.evaluate(
          (element) =>
            element.contains(document.activeElement) ||
            document.activeElement === document.body,
        ),
      ).toBe(true);
    }
    await page.keyboard.press("Escape");
    await expect(viewer).toHaveCount(0);
    await expect(trigger).toBeFocused();
    expect(mockTable.commands).toHaveLength(0);
    await trigger.click();
    await viewer.getByRole("button", { name: "Close zone viewer" }).click();
    await expect(trigger).toBeFocused();
    expect(mockTable.counts()).toEqual({ sessions: 1, connections: 1 });
  });

  test(`${format}: private filtering survives nested inspection and closes search once`, async ({
    page,
    mockTable,
  }) => {
    const view = { ...snapshotFixture(format), id: roomId };
    const card = view.cards[0];
    view.cards = ["Alpha", "Beta"].map((name, index) => ({
      ...card,
      id: `library-${index}`,
      zone: "library",
      definition: { ...card.definition!, name, faces: [] },
    }));
    mockTable.onCommand = async (command) => {
      await mockTable.acknowledge(
        { ...mockTable.view, revision: mockTable.view.revision + 1 },
        command.id,
        async () => {
          await expect(
            page.locator(".draw-controls button").first(),
          ).toBeEnabled();
        },
      );
    };
    await mockTable.open(view);
    const trigger = page.getByRole("button", {
      name: "library 59",
      exact: true,
    });
    await trigger.click();
    const viewer = page.getByRole("dialog", { name: "Zone viewer" });
    const search = viewer.getByRole("searchbox", {
      name: "Search library cards",
    });
    await search.fill("Beta");
    await expect(viewer.locator(".zone-grid > div > small")).toHaveText(["#2"]);
    const inspect = viewer.getByRole("button", {
      name: "Inspect Beta",
      exact: true,
    });
    await inspect.click();
    const details = page.getByRole("dialog", { name: "Card inspector" });
    await expect(details).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(details).toHaveCount(0);
    await expect(inspect).toBeFocused();
    await expect(search).toHaveValue("Beta");
    expect(mockTable.commands.map((command) => command.type)).toEqual([
      "SEARCH",
    ]);
    await mockTable.snapshot(
      {
        ...mockTable.view,
        revision: mockTable.view.revision + 1,
        cards: view.cards.map((card) =>
          card.id === "library-1"
            ? {
                ...card,
                definition: { ...card.definition!, name: "Beta updated" },
              }
            : card,
        ),
      },
      async () => {
        await expect(
          viewer.getByRole("button", {
            name: "Inspect Beta updated",
            exact: true,
          }),
        ).toBeVisible();
      },
    );
    await expect(search).toHaveValue("Beta");
    await page.keyboard.press("Escape");
    await expect(viewer).toHaveCount(0);
    await expect(trigger).toBeFocused();
    await expect
      .poll(() => mockTable.commands.map((command) => command.type))
      .toEqual(["SEARCH", "CLOSE_SEARCH"]);
    await page.keyboard.press("Escape");
    expect(mockTable.commands).toHaveLength(2);
  });
}

for (const format of ["COMMANDER", "STANDARD"] satisfies GameFormat[]) {
  test(`${format}: nested menu and artwork dismiss before the zone viewer`, async ({
    page,
    mockTable,
  }) => {
    const view = { ...snapshotFixture(format), id: roomId };
    const card = view.cards[0];
    view.cards = [
      {
        ...card,
        zone: "graveyard",
        definition: {
          ...card.definition!,
          name: "Preview card",
          faces: [],
          image:
            'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="280"/>',
        },
      },
    ];
    await mockTable.open(view);
    await page
      .getByRole("button", { name: "graveyard 0", exact: true })
      .click();
    const viewer = page.getByRole("dialog", { name: "Zone viewer" });
    const cardButton = viewer.getByRole("button", {
      name: "Inspect Preview card",
      exact: true,
    });
    await cardButton.focus();
    const artwork = page.getByRole("tooltip", {
      name: "Artwork: Preview card",
    });
    await expect(artwork).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(artwork).toHaveCount(0);
    await expect(viewer).toBeVisible();
    const actions = viewer.getByRole("button", {
      name: "Actions for Preview card",
    });
    await actions.click();
    const menu = page.getByRole("menu", { name: "Card actions" });
    await expect(
      menu.getByRole("menuitem", { name: "Play to battlefield", exact: true }),
    ).toBeVisible();
    expect(
      await menu.evaluate((element) => element.matches(":popover-open")),
    ).toBe(true);
    await page.keyboard.press("Escape");
    await expect(menu).toHaveCount(0);
    await expect(actions).toBeFocused();
    await expect(viewer).toBeVisible();
    await actions.click();
    await menu.getByRole("menuitem", { name: "Details", exact: true }).click();
    const details = page.getByRole("dialog", { name: "Card inspector" });
    await expect(details).toBeVisible();
    await details.getByRole("button", { name: "Close inspector" }).click();
    await expect(actions).toBeFocused();
    await expect(viewer).toBeVisible();
    expect(mockTable.commands).toHaveLength(0);
  });

  test(`${format}: pending search closure restores focus after ack without stealing later focus`, async ({
    page,
    mockTable,
  }) => {
    const view = { ...snapshotFixture(format), id: roomId };
    view.cards = [];
    mockTable.onCommand = async (command) => {
      if (command.type === "SEARCH")
        await mockTable.acknowledge(
          { ...view, revision: view.revision + 1 },
          command.id,
          async () => {
            await expect(
              page.locator(".draw-controls button").first(),
            ).toBeEnabled();
          },
        );
    };
    await mockTable.open(view);
    for (const moveFocus of [false, true]) {
      const trigger = page.getByRole("button", {
        name: "library 59",
        exact: true,
      });
      await trigger.click();
      const viewer = page.getByRole("dialog", { name: "Zone viewer" });
      await expect(viewer).toContainText("No visible cards in this zone.");
      await expect(page.locator(".draw-controls button").first()).toBeEnabled();
      await viewer.getByRole("button", { name: "Close zone viewer" }).click();
      await expect(viewer).toHaveCount(0);
      await expect(trigger).toBeDisabled();
      await expect
        .poll(
          () =>
            mockTable.commands.filter(
              (command) => command.type === "CLOSE_SEARCH",
            ).length,
        )
        .toBe(moveFocus ? 2 : 1);
      await page.keyboard.press("Escape");
      const header = page.getByRole("link", { name: /COMMANDER TABLE/ });
      if (moveFocus) await header.focus();
      const close = mockTable.commands.at(-1)!;
      await mockTable.acknowledge(
        { ...view, revision: view.revision + 3 },
        close.id,
        async () => {
          await expect(trigger).toBeEnabled();
        },
      );
      await expect(moveFocus ? header : trigger).toBeFocused();
    }
    expect(mockTable.commands.map((command) => command.type)).toEqual([
      "SEARCH",
      "CLOSE_SEARCH",
      "SEARCH",
      "CLOSE_SEARCH",
    ]);
  });

  test(`${format}: narrow viewer preserves concealed cards and empty filtered results`, async ({
    page,
    mockTable,
  }) => {
    await page.setViewportSize({ width: 380, height: 650 });
    const view = { ...snapshotFixture(format), id: roomId };
    view.cards = [
      {
        ...view.cards[0],
        zone: "library",
        faceDown: true,
        definition: undefined,
        editable: false,
      },
    ];
    await mockTable.open(view);
    await page.getByRole("button", { name: "library 59", exact: true }).click();
    const viewer = page.getByRole("dialog", { name: "Zone viewer" });
    await expect(
      viewer.getByRole("button", {
        name: "Inspect Face-down card",
        exact: true,
      }),
    ).toBeVisible();
    await expect(page.getByRole("tooltip")).toHaveCount(0);
    const search = viewer.getByRole("searchbox", {
      name: "Search library cards",
    });
    await search.fill("Private card name");
    await expect(viewer).toContainText("No cards match your search.");
    await viewer.getByRole("button", { name: "Clear search" }).click();
    await viewer
      .getByRole("button", { name: "Actions for Face-down card" })
      .click();
    const menu = page.getByRole("menu", { name: "Card actions" });
    await expect(
      menu.getByRole("menuitem", { name: "Play to battlefield", exact: true }),
    ).toHaveCount(0);
    await page.keyboard.press("Escape");
    const bounds = await viewer.boundingBox();
    expect(bounds).not.toBeNull();
    expect(bounds!.x).toBeGreaterThanOrEqual(0);
    expect(bounds!.y).toBeGreaterThanOrEqual(0);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(380);
    expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(650);
    await page.keyboard.press("Escape");
    await expect(viewer).toHaveCount(0);
    expect(mockTable.commands.map((command) => command.type)).toEqual([
      "SEARCH",
    ]);
    // Pending SEARCH prevents CLOSE_SEARCH submission; dismissal never queues it.
    const searchCommand = mockTable.commands[0];
    await mockTable.acknowledge(view, searchCommand.id, async () => {
      await expect(
        page.getByRole("button", { name: "library 59", exact: true }),
      ).toBeEnabled();
    });
    expect(mockTable.commands).toHaveLength(1);
  });

  test(`${format}: disconnected viewer permits inspection but sends no card actions`, async ({
    page,
    mockTable,
  }) => {
    const view = { ...snapshotFixture(format), id: roomId };
    view.cards = [{ ...view.cards[0], zone: "graveyard" }];
    await mockTable.install(view);
    let closeSocket: (() => Promise<void>) | undefined;
    let disconnected = false;
    const messages: unknown[] = [];
    await page.routeWebSocket("**/ws?room=**", (socket) => {
      if (disconnected) {
        void socket.close();
        return;
      }
      closeSocket = () => socket.close();
      socket.onMessage((message) => messages.push(message));
    });
    await page.goto(`/room/${roomId}`);
    await expect(page.getByText("Connected", { exact: true })).toBeVisible();
    await page
      .getByRole("button", { name: "graveyard 0", exact: true })
      .click();
    disconnected = true;
    await closeSocket!();
    await expect(page.locator(".draw-controls button").first()).toBeDisabled();
    const viewer = page.getByRole("dialog", { name: "Zone viewer" });
    await viewer.getByRole("button", { name: /^Actions for/ }).click();
    const menu = page.getByRole("menu", { name: "Card actions" });
    await expect(
      menu.getByRole("menuitem", { name: "Play to battlefield", exact: true }),
    ).toHaveCount(0);
    await menu.getByRole("menuitem", { name: "Details", exact: true }).click();
    const details = page.getByRole("dialog", { name: "Card inspector" });
    await expect(details).toBeVisible();
    await expect(details.getByLabel("Move to zone")).toHaveCount(0);
    await page.keyboard.press("Escape");
    await page.keyboard.press("Escape");
    await expect(viewer).toHaveCount(0);
    expect(messages).toHaveLength(0);
  });
}
