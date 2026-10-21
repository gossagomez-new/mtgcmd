import {
  test,
  expect,
  roomId,
  type TableFixture,
} from "./support/tableFixture";
import { snapshotFixture, definitionFixture } from "../src/protocolFixtures";
import type { GameFormat, Snapshot } from "../src/protocol";

function table(format: GameFormat) {
  const view = snapshotFixture(format);
  view.id = roomId;
  view.libraryInspection = null;
  view.players[0].counts.library = 20;
  return view;
}
function inspection(view: Snapshot, mode: "look" | "scry" = "scry") {
  const cards = ["First", "Second", "Third"].map((name, index) => ({
    ...view.cards[0],
    id: `library-${index}`,
    zone: "library",
    zoneOwner: 0,
    owner: 0,
    controller: 0,
    editable: false,
    definition: { ...definitionFixture(), id: name, name, faces: [] },
  }));
  return {
    ...view,
    revision: view.revision + 1,
    cards: [...view.cards, ...cards],
    libraryInspection: {
      id: "inspection",
      target: 0,
      mode,
      cardIds: cards.map((card) => card.id),
    },
    search: null,
  };
}
async function opened(mock: TableFixture, mode: "look" | "scry" = "scry") {
  await mock.snapshot(inspection(mock.view, mode), async () => {
    await expect(
      mock.page.getByRole("dialog", {
        name: mode === "look" ? "Look at top cards" : "Scry library",
      }),
    ).toBeVisible();
  });
}
for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: bounded controls submit counts once without opening a search`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    await expect(
      page.getByRole("spinbutton", { name: "Number of top cards" }),
    ).toBeVisible();
    await page
      .getByRole("spinbutton", { name: "Number of top cards" })
      .fill("3");
    await page
      .getByRole("button", { name: "Look at top", exact: true })
      .click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "LOOK_TOP",
      data: { target: 0, count: 3 },
    });
    await expect(
      page.getByRole("button", { name: "Scry", exact: true }),
    ).toBeDisabled();
    await mockTable.acknowledge(
      inspection(mockTable.view, "look"),
      mockTable.commands[0].id,
      async () => {
        await expect(
          page.getByRole("dialog", { name: "Look at top cards" }),
        ).toBeVisible();
      },
    );
    const dialog = page.getByRole("dialog", { name: "Look at top cards" });
    await expect(
      dialog.getByRole("button", { name: "Inspect First", exact: true }),
    ).toBeVisible();
    await expect(
      dialog.getByText("Private card name", { exact: true }),
    ).toHaveCount(0);
    await dialog
      .getByRole("button", { name: "Close inspection", exact: true })
      .click();
    await expect.poll(() => mockTable.commands.length).toBe(2);
    expect(mockTable.commands[1]).toMatchObject({
      type: "CLOSE_INSPECTION",
      data: { inspectionId: "inspection" },
    });
    expect(
      mockTable.commands.every((command) => command.type !== "SEARCH"),
    ).toBe(true);
  });
  test(`${format}: scry stages an ordered partition and submits it atomically`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    await opened(mockTable);
    const dialog = page.getByRole("dialog", { name: "Scry library" });
    await dialog
      .getByRole("button", { name: "Move First to bottom", exact: true })
      .click();
    await dialog
      .getByRole("button", { name: "Move Third earlier", exact: true })
      .click();
    expect(mockTable.commands).toHaveLength(0);
    const current = mockTable.view;
    await mockTable.snapshot(
      { ...current, revision: current.revision + 1, phase: "Main 2" },
      async () => {
        await expect(page.locator(".turn-strip")).toContainText("Main 2");
      },
    );
    await dialog
      .getByRole("button", { name: "Confirm scry", exact: true })
      .click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "APPLY_SCRY",
      data: {
        inspectionId: "inspection",
        top: ["library-2", "library-1"],
        bottom: ["library-0"],
      },
    });
    await expect(
      dialog.getByRole("button", { name: "Confirm scry", exact: true }),
    ).toBeDisabled();
  });
  test(`${format}: reload resumes the server selection and Escape cancels without replay`, async ({
    page,
    mockTable,
  }) => {
    const view = inspection(table(format));
    await mockTable.open(view);
    const dialog = page.getByRole("dialog", { name: "Scry library" });
    await expect(dialog).toBeVisible();
    await dialog
      .getByRole("button", { name: "Move First to bottom", exact: true })
      .click();
    await page.reload();
    await expect(dialog).toBeVisible();
    await expect(
      dialog
        .getByRole("group", { name: "Keep on top" })
        .getByRole("button", { name: "Inspect First", exact: true }),
    ).toBeVisible();
    expect(mockTable.commands).toHaveLength(0);
    await dialog.press("Escape");
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "CLOSE_INSPECTION",
      data: { inspectionId: "inspection" },
    });
  });
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: legacy snapshots disable unsupported library actions`, async ({
    page,
    mockTable,
  }) => {
    const legacy = table(format);
    delete legacy.libraryInspection;
    await mockTable.open(legacy);
    for (const name of ["Look at top", "Exile top", "Scry"]) {
      await expect(
        page.getByRole("button", { name, exact: true }),
      ).toBeDisabled();
    }
    expect(mockTable.commands).toHaveLength(0);
  });
  test(`${format}: numeric limits and empty libraries guard exact exile submission`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const input = page.getByRole("spinbutton", { name: "Number of top cards" });
    const exile = page.getByRole("button", { name: "Exile top", exact: true });
    for (const value of ["0", "101", "1.5", ""]) {
      await input.fill(value);
      await expect(exile).toBeDisabled();
    }
    await input.fill("100");
    await exile.click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "EXILE_TOP",
      data: { target: 0, count: 100 },
    });
    await expect(exile).toBeDisabled();
    const next = {
      ...mockTable.view,
      revision: mockTable.view.revision + 1,
      players: mockTable.view.players.map((player) => ({
        ...player,
        counts: { ...player.counts, library: 0 },
      })),
    };
    await mockTable.acknowledge(next, mockTable.commands[0].id, async () => {
      await expect(
        page
          .locator(".zone-shortcuts")
          .getByRole("button", { name: /^library/ }),
      ).toContainText("0");
    });
    await expect(exile).toBeDisabled();
    expect(mockTable.commands).toHaveLength(1);
  });
  test(`${format}: failed confirmation preserves draft and never retries automatically`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(inspection(table(format)));
    const dialog = page.getByRole("dialog", { name: "Scry library" });
    await expect(dialog).toBeVisible();
    await dialog
      .getByRole("button", { name: "Move First to bottom", exact: true })
      .click();
    const confirm = dialog.getByRole("button", {
      name: "Confirm scry",
      exact: true,
    });
    await confirm.click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    mockTable.sendRaw(
      JSON.stringify({
        type: "error",
        message: "Invalid scry partition",
        view: mockTable.view,
      }),
    );
    await expect(page.getByRole("alert")).toContainText(
      "Invalid scry partition",
    );
    await expect(confirm).toBeEnabled();
    expect(mockTable.commands).toHaveLength(1);
    await confirm.click();
    await expect.poll(() => mockTable.commands.length).toBe(2);
    expect(mockTable.commands[1].data).toEqual(mockTable.commands[0].data);
    const next = {
      ...mockTable.view,
      revision: mockTable.view.revision + 1,
      libraryInspection: null,
      cards: mockTable.view.cards.filter((card) => card.zone !== "library"),
    };
    await mockTable.acknowledge(next, mockTable.commands[1].id, async () => {
      await expect(dialog).toHaveCount(0);
    });
    expect(mockTable.commands).toHaveLength(2);
  });
  test(`${format}: keyboard ordering and cancellation restore the action trigger`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(table(format));
    const trigger = page.getByRole("button", { name: "Scry", exact: true });
    await trigger.click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    await mockTable.acknowledge(
      inspection(mockTable.view),
      mockTable.commands[0].id,
      async () => {
        await expect(
          page.getByRole("dialog", { name: "Scry library" }),
        ).toBeVisible();
      },
    );
    const dialog = page.getByRole("dialog", { name: "Scry library" });
    const move = dialog.getByRole("button", {
      name: "Move Second to bottom",
      exact: true,
    });
    await move.focus();
    await move.press("Enter");
    await expect(
      dialog
        .getByRole("group", { name: "Put on bottom" })
        .getByRole("button", { name: "Inspect Second", exact: true }),
    ).toBeVisible();
    await dialog.press("Escape");
    await expect.poll(() => mockTable.commands.length).toBe(2);
    const next = {
      ...mockTable.view,
      revision: mockTable.view.revision + 1,
      libraryInspection: null,
      cards: mockTable.view.cards.filter((card) => card.zone !== "library"),
    };
    await mockTable.acknowledge(next, mockTable.commands[1].id, async () => {
      await expect(dialog).toHaveCount(0);
    });
    await expect(trigger).toBeFocused();
  });
  test(`${format}: granted targets are selectable and revocation resets selection`, async ({
    page,
    mockTable,
  }) => {
    const initial = table(format);
    initial.players.push({
      ...initial.players[0],
      seat: 1,
      name: "Bob",
      privateAccess: false,
    });
    await mockTable.open(initial);
    const owner = page.getByRole("combobox", { name: "Library owner" });
    await expect(
      owner.getByRole("option", { name: "Bob", exact: true }),
    ).toHaveCount(0);
    const granted = {
      ...mockTable.view,
      revision: mockTable.view.revision + 1,
      players: mockTable.view.players.map((player) => ({
        ...player,
        privateAccess: true,
      })),
    };
    await mockTable.snapshot(granted, async () => {
      await expect(
        owner.getByRole("option", { name: "Bob", exact: true }),
      ).toHaveCount(1);
    });
    await owner.selectOption("1");
    await page
      .getByRole("button", { name: "Look at top", exact: true })
      .click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "LOOK_TOP",
      data: { target: 1, count: 1 },
    });
    const revoked = {
      ...mockTable.view,
      revision: mockTable.view.revision + 1,
      players: mockTable.view.players.map((player) => ({
        ...player,
        privateAccess: player.seat === 0,
      })),
    };
    await mockTable.acknowledge(revoked, mockTable.commands[0].id, async () => {
      await expect(owner).toHaveValue("0");
    });
  });
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: details stay read-only without cancelling the bounded selection`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(inspection(table(format), "look"));
    const panel = page.getByRole("dialog", { name: "Look at top cards" });
    await expect(panel).toBeVisible();
    await panel
      .getByRole("button", { name: "Inspect First", exact: true })
      .click();
    const details = page.getByRole("dialog", { name: "Card inspector" });
    await expect(details).toBeVisible();
    await expect(
      details.getByRole("button", { name: "Play to battlefield", exact: true }),
    ).toHaveCount(0);
    await details
      .getByRole("button", { name: "Close inspector", exact: true })
      .click();
    await expect(panel).toBeVisible();
    expect(mockTable.commands).toHaveLength(0);
  });
  test(`${format}: bounded content stays private on small screens and invalidation removes it`, async ({
    page,
    mockTable,
  }) => {
    await page.setViewportSize({ width: 390, height: 700 });
    const view = inspection(table(format));
    view.cards.find((card) => card.id === "library-0")!.definition = undefined;
    view.cards.push({
      ...view.cards[0],
      id: "outside-prefix",
      zone: "library",
      zoneOwner: 0,
      definition: {
        ...definitionFixture(),
        name: "Uninspected secret",
        faces: [],
      },
    });
    await mockTable.open(view);
    const panel = page.getByRole("dialog", { name: "Scry library" });
    await expect(panel).toBeVisible();
    await expect(
      panel.getByRole("button", {
        name: "Inspect Face-down card",
        exact: true,
      }),
    ).toHaveCount(1);
    await expect(
      panel.getByText("Uninspected secret", { exact: true }),
    ).toHaveCount(0);
    const box = await panel.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(390);
    const counts = mockTable.counts();
    await mockTable.snapshot(
      {
        ...mockTable.view,
        revision: view.revision + 1,
        libraryInspection: null,
        cards: view.cards.filter((card) => card.zone !== "library"),
      },
      async () => {
        await expect(panel).toHaveCount(0);
      },
    );
    expect(mockTable.commands).toHaveLength(0);
    expect(mockTable.counts()).toEqual(counts);
  });
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: outside dismissal cancels once and waits for acknowledgment`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.open(inspection(table(format), "look"));
    const panel = page.getByRole("dialog", { name: "Look at top cards" });
    await expect(panel).toBeVisible();
    await page.locator(".turn-strip .eyebrow").click();
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0]).toMatchObject({
      type: "CLOSE_INSPECTION",
      data: { inspectionId: "inspection" },
    });
    await expect(panel).toBeVisible();
    await mockTable.acknowledge(
      {
        ...mockTable.view,
        revision: mockTable.view.revision + 1,
        libraryInspection: null,
        cards: mockTable.view.cards.filter((card) => card.zone !== "library"),
      },
      mockTable.commands[0].id,
      async () => {
        await expect(panel).toHaveCount(0);
      },
    );
    expect(mockTable.commands).toHaveLength(1);
  });
}
