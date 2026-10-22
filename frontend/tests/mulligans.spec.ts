import { test, expect, roomId } from "./support/tableFixture";
import { snapshotFixture } from "../src/protocolFixtures";
import type { Snapshot } from "../src/protocol";

function opening(format: "STANDARD" | "COMMANDER"): Snapshot {
  const base = snapshotFixture(format);
  return {
    ...base,
    id: roomId,
    status: "opening",
    players: [{ ...base.players[0], counts: { hand: 7, library: 52 } }],
    cards: Array.from({ length: 7 }, (_, index) => ({
      ...base.cards[0],
      id: `hand-${index}`,
      zone: "hand",
      editable: false,
      definition: { ...base.cards[0].definition!, name: `Opening ${index}` },
    })),
    mulligans: {
      mode: "LONDON",
      freeMulligans: format === "STANDARD" ? 0 : 1,
      players: [
        {
          seat: 0,
          redraws: format === "STANDARD" ? 2 : 3,
          kept: false,
          bottomCount: 2,
        },
      ],
    },
  };
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: unlimited redraws keep seven, show face artwork and recover rejected keeps without replay`, async ({
    page,
    mockTable,
  }) => {
    const view = opening(format);
    view.mulligans!.mode = "UNLIMITED";
    view.mulligans!.players[0] = {
      seat: 0,
      redraws: 9,
      kept: false,
      bottomCount: 0,
    };
    const image =
      "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='30'%3E%3C/svg%3E";
    view.cards[0].definition!.faces = [
      { name: "Front", image, type: "Creature", text: "Rules" },
    ];
    await mockTable.open(view);
    await expect(
      page.getByRole("img", { name: "Opening 0", exact: true }),
    ).toHaveAttribute("src", image);
    await expect(
      page.getByRole("button", { name: "Mulligan", exact: true }),
    ).toBeEnabled();
    await page
      .getByRole("button", { name: "Inspect Opening 0", exact: true })
      .click();
    const details = page.getByRole("dialog", { name: "Card inspector" });
    await expect(details).toBeVisible();
    await expect(
      details.getByRole("button", { name: "Play to battlefield", exact: true }),
    ).toHaveCount(0);
    await page
      .getByRole("button", { name: "Close inspector", exact: true })
      .click();
    await page.getByRole("button", { name: "Keep hand", exact: true }).click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "KEEP_HAND",
      data: { bottom: [] },
    });
    mockTable.sendRaw(
      JSON.stringify({
        type: "error",
        message: "Opening hand changed; review it",
        view,
      }),
    );
    await expect(page.getByRole("alert")).toContainText(
      "Opening hand changed; review it",
    );
    await expect(
      page.getByRole("button", { name: "Keep hand", exact: true }),
    ).toBeEnabled();
    await page.reload();
    await expect(
      page.getByRole("button", { name: "Keep hand", exact: true }),
    ).toBeEnabled();
    expect(mockTable.commands).toHaveLength(1);
  });

  test(`${format}: empty and maximum-debt hands allow keeping but no further redraw`, async ({
    page,
    mockTable,
  }) => {
    const view = opening(format);
    view.mulligans!.players[0] = {
      seat: 0,
      redraws: 7 + view.mulligans!.freeMulligans,
      kept: false,
      bottomCount: 7,
    };
    await mockTable.open(view);
    await expect(
      page.getByRole("button", { name: "Mulligan", exact: true }),
    ).toBeDisabled();
    for (let index = 0; index < 7; index++)
      await page
        .getByRole("button", { name: `Bottom Opening ${index}`, exact: true })
        .click();
    await expect(
      page.getByRole("button", { name: "Keep hand", exact: true }),
    ).toBeEnabled();
    const empty = {
      ...view,
      revision: view.revision + 1,
      cards: [],
      mulligans: {
        ...view.mulligans!,
        players: [{ seat: 0, redraws: 0, kept: false, bottomCount: 0 }],
      },
    };
    await mockTable.snapshot(empty, async () => {
      await expect(
        page.getByRole("button", { name: "Mulligan", exact: true }),
      ).toBeDisabled();
      await expect(
        page.getByRole("button", { name: "Keep hand", exact: true }),
      ).toBeEnabled();
    });
    await page.getByRole("button", { name: "Keep hand", exact: true }).click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "KEEP_HAND",
      data: { bottom: [] },
    });
  });

  test(`${format}: host settings are visible and changes wait for server confirmation`, async ({
    page,
    mockTable,
  }) => {
    const view = opening(format);
    view.status = "lobby";
    await mockTable.open(view);
    await expect(
      page.getByRole("region", { name: "Mulligan settings" }),
    ).toBeVisible();
    await page.getByLabel("Free mulligans (London only)").fill("2");
    await page
      .getByLabel("Mulligan mode", { exact: true })
      .selectOption("UNLIMITED");
    await expect(
      page.getByLabel("Free mulligans (London only)"),
    ).toBeDisabled();
    await page
      .getByRole("button", { name: "Apply mulligan settings", exact: true })
      .click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "CONFIGURE_MULLIGANS",
      data: { mode: "UNLIMITED", freeMulligans: 2 },
    });
    await expect(
      page.getByRole("button", {
        name: "Apply mulligan settings",
        exact: true,
      }),
    ).toBeDisabled();
    const updated = {
      ...view,
      revision: view.revision + 1,
      mulligans: {
        ...view.mulligans!,
        mode: "UNLIMITED" as const,
        freeMulligans: 2,
        players: view.mulligans!.players.map((player) => ({
          ...player,
          redraws: 0,
          kept: false,
          bottomCount: 0,
        })),
      },
      players: view.players.map((player) => ({ ...player, ready: false })),
    };
    await mockTable.acknowledge(updated, mockTable.commands[0].id, async () => {
      await expect(
        page.getByText("Unlimited seven-card redraws · no bottoming", {
          exact: true,
        }),
      ).toBeVisible();
    });
  });

  test(`${format}: non-host sees agreed settings without configuration controls`, async ({
    page,
    mockTable,
  }) => {
    const view = opening(format);
    view.status = "lobby";
    view.host = 1;
    await mockTable.open(view);
    await expect(
      page.getByRole("region", { name: "Mulligan settings" }),
    ).toBeVisible();
    await expect(page.getByLabel("Mulligan mode", { exact: true })).toHaveCount(
      0,
    );
    await expect(
      page.getByRole("button", {
        name: "Apply mulligan settings",
        exact: true,
      }),
    ).toHaveCount(0);
  });

  test(`${format}: opening keeps ordered cards once and blocks gameplay`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(opening(format));
    await expect(
      page.getByRole("heading", { name: "Choose your opening hand" }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Draw a card", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Keep hand", exact: true }),
    ).toBeDisabled();
    await page
      .getByRole("button", { name: "Bottom Opening 3", exact: true })
      .click();
    await page
      .getByRole("button", { name: "Bottom Opening 1", exact: true })
      .click();
    await page
      .getByRole("button", { name: "Move Opening 1 earlier", exact: true })
      .click();
    await page.getByRole("button", { name: "Keep hand", exact: true }).click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "KEEP_HAND",
      data: { bottom: ["hand-1", "hand-3"] },
    });
    await expect(
      page.getByRole("button", { name: "Keep hand", exact: true }),
    ).toBeDisabled();
    const kept = opening(format);
    kept.revision++;
    kept.mulligans!.players[0].kept = true;
    kept.mulligans!.players[0].bottomCount = 0;
    await mockTable.acknowledge(kept, mockTable.commands[0].id, async () => {
      await expect(
        page
          .getByRole("status")
          .filter({ hasText: "Waiting for other players" }),
      ).toBeVisible();
    });
    await page.reload();
    await expect(
      page.getByText("Waiting for other players", { exact: true }),
    ).toBeVisible();
    expect(mockTable.commands).toHaveLength(1);
  });

  test(`${format}: redraw resets local bottom choices and snapshot updates do not`, async ({
    page,
    mockTable,
  }) => {
    const view = opening(format);
    await mockTable.open(view);
    await page
      .getByRole("button", { name: "Bottom Opening 0", exact: true })
      .focus();
    await page.keyboard.press("Space");
    await mockTable.snapshot(
      { ...view, revision: view.revision + 1 },
      async () => {
        await expect(
          page.getByRole("button", { name: "Bottom Opening 0", exact: true }),
        ).toHaveAttribute("aria-pressed", "true");
      },
    );
    await page.getByRole("button", { name: "Mulligan", exact: true }).click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({ type: "MULLIGAN", data: {} });
    const next = {
      ...view,
      revision: view.revision + 2,
      cards: view.cards.map((card) => ({ ...card, id: `${card.id}-new` })),
    };
    await mockTable.acknowledge(next, mockTable.commands[0].id, async () => {
      await expect(
        page.getByRole("button", { name: "Bottom Opening 0", exact: true }),
      ).toHaveAttribute("aria-pressed", "false");
    });
  });
}
