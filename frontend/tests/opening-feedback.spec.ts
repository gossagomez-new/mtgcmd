import { test, expect, roomId } from "./support/tableFixture";
import { snapshotFixture, definitionFixture } from "../src/protocolFixtures";
import type { GameFormat, Snapshot } from "../src/protocol";
import type { WebSocketRoute } from "@playwright/test";

const image =
  'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="280"/>';

function opening(format: GameFormat): Snapshot {
  const base = snapshotFixture(format);
  const players = Array.from({ length: base.seatCapacity }, (_, seat) => ({
    ...base.players[0],
    seat,
    name: ["Alice", "Bob", "Carol", "Dan"][seat],
    privateAccess: seat === 0,
    counts: { hand: 7, library: 52 },
  }));
  return {
    ...base,
    id: roomId,
    status: "opening",
    players,
    cards: [{ ...base.cards[0], zone: "hand", editable: false }],
    mulligans: {
      mode: "LONDON",
      freeMulligans: format === "COMMANDER" ? 1 : 0,
      players: players.map((player) => ({
        seat: player.seat,
        redraws: 0,
        kept: player.seat === 0,
        bottomCount: 0,
      })),
    },
  };
}

function playing(format: GameFormat): Snapshot {
  const view = opening(format);
  const card = {
    ...view.cards[0],
    editable: true,
    definition: {
      ...definitionFixture(),
      name: "Hand artwork",
      image,
      faces: [],
    },
  };
  return {
    ...view,
    status: "playing",
    mulligans: undefined,
    cards: [
      card,
      {
        ...card,
        id: "battlefield",
        zone: "battlefield",
        definition: { ...card.definition, name: "Battlefield artwork" },
      },
      { ...card, id: "second-battlefield", zone: "battlefield" },
      {
        ...card,
        id: "hidden-hand",
        zoneOwner: 1,
        owner: 1,
        controller: 1,
        definition: undefined,
        editable: false,
      },
    ],
  };
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  const initialMessage =
    format === "STANDARD"
      ? "Waiting for Bob to keep their opening hand."
      : "Waiting for Bob, Carol, and Dan to keep their opening hands.";

  test(`${format}: long names wrap on narrow screens`, async ({
    page,
    mockTable,
  }) => {
    const view = opening(format);
    const name = "W".repeat(40);
    view.players[1].name = name;
    await page.setViewportSize({ width: 390, height: 780 });
    await mockTable.open(view);
    const status = page.getByRole("status", {
      name: "Opening hand status",
      exact: true,
    });
    await expect(status).toContainText(name);
    for (const element of [
      status,
      page.getByRole("list", { name: "Opening-hand progress" }),
    ]) {
      expect(
        await element.evaluate((node) => node.scrollWidth <= node.clientWidth),
      ).toBe(true);
    }
    expect(mockTable.commands).toHaveLength(0);
  });

  test(`${format}: names pending peers and leaves unchanged announcements alone`, async ({
    page,
    mockTable,
  }) => {
    const view = opening(format);
    await mockTable.open(view);
    const status = page.getByRole("status", {
      name: "Opening hand status",
      exact: true,
    });
    await expect(status).toHaveText(initialMessage);
    await expect(status).toHaveAttribute("aria-live", "polite");
    await expect(status).toHaveAttribute("aria-atomic", "true");
    const observation = await status.evaluateHandle((element) => {
      const result = { mutations: 0 };
      const observer = new MutationObserver((records) => {
        result.mutations += records.length;
      });
      observer.observe(element, {
        childList: true,
        characterData: true,
        subtree: true,
      });
      return { result, observer };
    });
    const redraw = {
      ...view,
      revision: view.revision + 1,
      mulligans: {
        ...view.mulligans!,
        players: view.mulligans!.players.map((player) =>
          player.seat === 1
            ? {
                ...player,
                redraws: 1,
                bottomCount: format === "STANDARD" ? 1 : 0,
              }
            : player,
        ),
      },
    };
    await mockTable.snapshot(redraw, async () => {
      await expect(
        page.getByRole("list", { name: "Opening-hand progress" }),
      ).toContainText("Bob: Choosing · 1 mulligan(s)");
    });
    expect(await observation.evaluate(({ result }) => result.mutations)).toBe(
      0,
    );
    await observation.evaluate(({ observer }) => observer.disconnect());
    await observation.dispose();
    const next = {
      ...redraw,
      revision: redraw.revision + 1,
      mulligans: {
        ...redraw.mulligans,
        players: redraw.mulligans.players.map((player) =>
          player.seat === 1
            ? { ...player, kept: true, bottomCount: 0 }
            : player,
        ),
      },
    };
    await mockTable.snapshot(next, async () => {
      await expect(status).toHaveText(
        format === "STANDARD"
          ? "Waiting for the game to begin."
          : "Waiting for Carol and Dan to keep their opening hands.",
      );
    });
    const ready = {
      ...next,
      revision: next.revision + 1,
      status: "playing",
      mulligans: {
        ...next.mulligans,
        players: next.mulligans.players.map((player) => ({
          ...player,
          kept: true,
          bottomCount: 0,
        })),
      },
    };
    await mockTable.snapshot(ready, async () => {
      await expect(page.locator("main.game-layout")).toBeVisible();
      await expect(status).toHaveCount(0);
    });
    expect(mockTable.commands).toHaveLength(0);
  });

  test(`${format}: waiting names follow server confirmation, name updates and reload`, async ({
    page,
    mockTable,
  }) => {
    const view = opening(format);
    view.mulligans!.players[0].kept = false;
    await mockTable.open(view);
    const status = page.getByRole("status", {
      name: "Opening hand status",
      exact: true,
    });
    await expect(status).toHaveCount(0);
    await page.getByRole("button", { name: "Keep hand", exact: true }).click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    await expect(status).toHaveCount(0);
    const kept = {
      ...view,
      revision: view.revision + 1,
      mulligans: {
        ...view.mulligans!,
        players: view.mulligans!.players.map((player) =>
          player.seat === 0 ? { ...player, kept: true } : player,
        ),
      },
    };
    await mockTable.acknowledge(kept, mockTable.commands[0].id, async () => {
      await expect(status).toHaveText(initialMessage);
    });
    const name = "<em>Bob</em> with a long player name";
    const renamed = {
      ...kept,
      revision: kept.revision + 1,
      players: kept.players.map((player) =>
        player.seat === 1 ? { ...player, name } : player,
      ),
    };
    await mockTable.snapshot(renamed, async () => {
      await expect(status).toContainText(name);
    });
    await expect(status.locator("em")).toHaveCount(0);
    await page.reload();
    await expect(page.getByText("Connected", { exact: true })).toBeVisible();
    await expect(status).toContainText(name);
    await expect(status).not.toContainText("Alice");
    await expect(
      page.getByRole("button", { name: "Keep hand", exact: true }),
    ).toHaveCount(0);
    expect(mockTable.commands).toHaveLength(1);
  });

  test(`${format}: another viewer seat retains waiting names through disconnect and reconnect`, async ({
    page,
    mockTable,
  }) => {
    const view = opening(format);
    view.you = view.seatCapacity - 1;
    view.mulligans!.players = view.mulligans!.players.map((player) => ({
      ...player,
      kept: player.seat !== 0,
    }));
    view.cards = view.cards.map((card) => ({
      ...card,
      owner: view.you,
      controller: view.you,
      zoneOwner: view.you,
    }));
    await mockTable.install(view);
    let socket: WebSocketRoute | undefined;
    let connections = 0;
    const messages: unknown[] = [];
    await page.routeWebSocket("**/ws?room=**", (route) => {
      socket = route;
      connections++;
      route.onMessage((message) => messages.push(message));
    });
    await page.goto(`/room/${roomId}`);
    await expect(page.getByText("Connected", { exact: true })).toBeVisible();
    const status = page.getByRole("status", {
      name: "Opening hand status",
      exact: true,
    });
    await expect(status).toHaveText(
      "Waiting for Alice to keep their opening hand.",
    );
    await page.clock.install();
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 1000));
    await socket!.close();
    await expect(
      page.getByText("Reconnecting…", { exact: true }),
    ).toBeVisible();
    await expect(status).toHaveText(
      "Waiting for Alice to keep their opening hand.",
    );
    await page.clock.fastForward(1000);
    await expect(page.getByText("Connected", { exact: true })).toBeVisible();
    await expect(status).toHaveText(
      "Waiting for Alice to keep their opening hand.",
    );
    expect(connections).toBe(2);
    expect(messages).toHaveLength(0);
  });

  test(`${format}: native drop tooltip is absent while hand artwork and inspection remain available`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(playing(format));
    const card = page
      .getByTestId("hand")
      .getByRole("button", { name: "Inspect Hand artwork", exact: true });
    expect(
      await card.evaluate(
        (element) => element.closest("[title]")?.getAttribute("title") ?? null,
      ),
    ).toBeNull();
    await card.hover();
    const preview = page.getByRole("tooltip", {
      name: "Artwork: Hand artwork",
      exact: true,
    });
    await expect(preview).toBeVisible();
    await page.waitForTimeout(1500);
    await expect(preview).toBeVisible();
    await expect(preview.locator("img")).toHaveAttribute("src", image);
    await page.keyboard.press("Escape");
    await expect(preview).toHaveCount(0);
    await card.focus();
    await expect(preview).toBeVisible();
    await page.keyboard.press("Enter");
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toBeVisible();
    await expect(
      page
        .getByTestId("hand")
        .getByRole("button", { name: "Inspect Face-down card", exact: true }),
    ).toHaveCount(0);
    expect(mockTable.commands).toHaveLength(0);
  });

  test(`${format}: hand drops retain feedback and permission/pending guards`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(playing(format));
    const hand = page.locator("section.hand");
    for (const id of ["external", "hidden-hand"]) {
      await hand.evaluate((element, identifier) => {
        const transfer = new DataTransfer();
        transfer.setData("application/card", identifier);
        element.dispatchEvent(
          new DragEvent("drop", { bubbles: true, dataTransfer: transfer }),
        );
      }, id);
    }
    expect(mockTable.commands).toHaveLength(0);
    await hand.evaluate((element) => {
      const transfer = new DataTransfer();
      transfer.setData("application/card", "battlefield");
      element.dispatchEvent(
        new DragEvent("dragover", {
          bubbles: true,
          cancelable: true,
          dataTransfer: transfer,
        }),
      );
    });
    await expect(hand).toHaveClass(/drop-active/);
    await hand.dispatchEvent("dragleave");
    await expect(hand).not.toHaveClass(/drop-active/);
    await page
      .getByTestId("your-battlefield")
      .getByRole("button", { name: "Inspect Battlefield artwork", exact: true })
      .dragTo(hand);
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "MOVE",
      data: { cardId: "battlefield", zone: "hand" },
    });
    await hand.evaluate((element) => {
      const transfer = new DataTransfer();
      transfer.setData("application/card", "second-battlefield");
      element.dispatchEvent(
        new DragEvent("drop", { bubbles: true, dataTransfer: transfer }),
      );
    });
    expect(mockTable.commands).toHaveLength(1);
    await expect(hand).not.toHaveClass(/drop-active/);
  });
}
