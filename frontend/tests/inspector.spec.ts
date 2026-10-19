import { type Locator } from "@playwright/test";
import {
  test,
  expect,
  roomId as room,
  type TableFixture,
} from "./support/tableFixture";
import { snapshotFixture } from "../src/protocolFixtures";
import type { Card, GameFormat, Snapshot } from "../src/protocol";

function namedCard(card: Card, id: string, name: string): Card {
  return {
    ...card,
    id,
    definition: card.definition && {
      ...card.definition,
      name,
      faces: [{ name, type: "Creature", text: "Rules", image: "" }],
    },
  };
}
function fixture(format: GameFormat = "COMMANDER"): Snapshot {
  const view = { ...snapshotFixture(format), id: room };
  view.players.push({ ...view.players[0], seat: 1, name: "Bob" });
  const card = view.cards[0];
  view.cards = [
    card,
    namedCard(card, "second", "Second card"),
    {
      ...namedCard(card, "attacker", "Enemy attacker"),
      owner: 1,
      controller: 1,
      combat: "attacker",
    },
    view.cards[1],
  ];
  return view;
}
async function inspector(mockTable: TableFixture, initial = fixture()) {
  const page = mockTable.page;
  let current = initial;
  const commands: { type: string; data: Record<string, unknown> }[] = [];
  const update = async (view: Snapshot, commandId = "test") => {
    // A visible scenario marker proves this snapshot was rendered before returning.
    const next = {
      ...view,
      revision: current.revision + 1,
      phase: `Fixture revision ${current.revision + 1}`,
    };
    current = next;
    await mockTable.acknowledge(next, commandId, async () => {
      await expect(page.locator(".turn-strip")).toContainText(next.phase);
    });
  };
  mockTable.onCommand = async (command) => {
    commands.push({ type: command.type, data: command.data });
    await update(current, command.id);
  };
  await mockTable.open(initial);
  await page
    .getByRole("button", { name: "Inspect Front", exact: true })
    .click();
  const panel = page.getByRole("dialog", { name: "Card inspector" });
  await expect(
    panel.getByRole("heading", { name: "Front", exact: true }),
  ).toBeVisible();
  return {
    panel,
    update,
    command: async (
      trigger: () => Promise<unknown>,
      type: string,
      data: Record<string, unknown> = {},
      cardId = "card",
    ) => {
      const count = commands.length;
      await trigger();
      await expect.poll(() => commands.length).toBe(count + 1);
      await mockTable.settled();
      expect(commands[count]).toEqual({ type, data: { cardId, ...data } });
      await expect(
        page.getByRole("button", {
          name: "Draw a card",
          exact: true,
          includeHidden: true,
        }),
      ).toBeEnabled();
    },
    commands,
  };
}
function click(panel: Locator, name: string) {
  return () => panel.getByRole("button", { name, exact: true }).click();
}

test("movement and identity actions retain exact payloads and commander restrictions", async ({
  mockTable,
}) => {
  const view = fixture();
  const { panel, update, command } = await inspector(mockTable, view);
  await expect(
    panel.getByLabel("Move to zone").locator("option[value=command]"),
  ).toHaveCount(0);
  await command(click(panel, "Exile face down"), "MOVE", {
    zone: "exile",
    faceDown: true,
  });
  await command(click(panel, "Tap"), "TAP");
  await command(click(panel, "Turn face down"), "FACE_DOWN");
  await command(click(panel, "Library top"), "MOVE", {
    zone: "library",
    top: true,
  });
  await command(click(panel, "Library bottom"), "MOVE", { zone: "library" });
  for (const zone of [
    "library",
    "hand",
    "battlefield",
    "graveyard",
    "exile",
    "stack",
  ]) {
    await command(
      () => panel.getByLabel("Move to zone").selectOption(zone),
      "MOVE",
      { zone },
    );
  }
  await update({
    ...view,
    cards: view.cards.map((c) =>
      c.id === "card"
        ? {
            ...c,
            commanderKey: "commander",
            zone: "graveyard",
            tapped: true,
            faceDown: true,
          }
        : c,
    ),
  });
  await expect(
    panel.getByRole("button", { name: "Play to battlefield", exact: true }),
  ).toBeVisible();
  await command(
    () => panel.getByLabel("Move to zone").selectOption("command"),
    "MOVE",
    { zone: "command" },
  );
  await command(click(panel, "Play to battlefield"), "MOVE", {
    zone: "battlefield",
  });
  await command(click(panel, "Play face down"), "MOVE", {
    zone: "battlefield",
    faceDown: true,
  });
  await update({
    ...view,
    cards: view.cards.map((c) => ({ ...c, tapped: true, faceDown: true })),
  });
  await command(click(panel, "Untap"), "TAP");
  await command(click(panel, "Turn face up"), "FACE_DOWN");
});

test("counters, relations, combat and reveals retain targets and filtering", async ({
  mockTable,
}) => {
  const { panel, command, update } = await inspector(mockTable);
  await panel.getByLabel("Card counter", { exact: true }).fill("   ");
  await expect(
    panel.getByRole("button", { name: "+ Counter", exact: true }),
  ).toBeDisabled();
  await panel.getByLabel("Card counter", { exact: true }).fill("charge");
  await command(click(panel, "+ Counter"), "CARD_COUNTER", {
    name: "charge",
    delta: 1,
  });
  await command(click(panel, "− Counter"), "CARD_COUNTER", {
    name: "charge",
    delta: -1,
  });
  await panel.getByLabel("Transfer control").selectOption("1");
  await command(click(panel, "Give control"), "CONTROL", { target: 1 });
  await expect(
    panel.getByLabel("Attach to").locator("option[value=card]"),
  ).toHaveCount(0);
  await panel.getByLabel("Attach to").selectOption("second");
  await command(click(panel, "Attach"), "ATTACH", {
    targetId: "second",
  });
  await update({
    ...fixture(),
    cards: fixture().cards.map((card) =>
      card.id === "card" ? { ...card, attachedTo: "second" } : card,
    ),
  });
  await command(click(panel, "Detach"), "ATTACH", { targetId: "" });
  await command(click(panel, "Mark attacker"), "COMBAT", {
    marker: "attacker",
  });
  await command(click(panel, "Clear combat"), "COMBAT", { marker: "" });
  await expect(
    panel.getByRole("button", { name: "Mark blocker", exact: true }),
  ).toBeDisabled();
  await expect(panel.getByLabel("Block attacker").locator("option")).toHaveText(
    ["Choose attacker", "Enemy attacker"],
  );
  await panel.getByLabel("Block attacker").selectOption("attacker");
  await command(click(panel, "Mark blocker"), "COMBAT", {
    marker: "blocks:attacker",
  });
  await command(click(panel, "Reveal to table"), "REVEAL", { audience: [1] });
  await command(click(panel, "End reveal"), "REVEAL", { audience: [] });
  await panel.getByText("Reveal to one player", { exact: true }).click();
  await expect(panel.getByLabel("Reveal audience")).toHaveValue("1");
  await command(click(panel, "Reveal to selected player"), "REVEAL", {
    audience: [1],
  });
  // A pending command temporarily unmounts editable controls, closing details.
  await panel.getByText("Reveal to one player", { exact: true }).click();
  await panel.getByLabel("Reveal audience").selectOption("0");
  await expect(panel.getByLabel("Transfer control")).toHaveValue("0");
});

for (const format of ["COMMANDER", "STANDARD"] as const) {
  test(`${format}: read-only public cards can be copied independently of editing`, async ({
    mockTable,
  }) => {
    const view = fixture(format);
    const session = await inspector(mockTable, view);
    await session.panel
      .getByLabel("Inspect another card")
      .selectOption("attacker");
    await expect(
      session.panel.getByRole("heading", {
        name: "Enemy attacker",
        exact: true,
      }),
    ).toBeVisible();
    await session.update({
      ...view,
      cards: view.cards.map((c) =>
        c.id === "attacker" ? { ...c, editable: false } : c,
      ),
    });
    await expect(
      session.panel.getByRole("button", { name: "Tap", exact: true }),
    ).toHaveCount(0);
    await session.command(
      click(session.panel, "Create token copy"),
      "COPY",
      {},
      "attacker",
    );
    for (const zone of ["hand", "library"]) {
      await session.update({
        ...view,
        cards: view.cards.map((c) =>
          c.id === "attacker" ? { ...c, zone, editable: false } : c,
        ),
      });
      await expect(
        session.panel.getByRole("button", {
          name: "Create token copy",
          exact: true,
        }),
      ).toHaveCount(0);
    }
    await session.update({ ...view, status: "finished" });
    await expect(
      session.panel.getByRole("button", { name: "Tap", exact: true }),
    ).toHaveCount(0);
    await expect(
      session.panel.getByRole("button", {
        name: "Create token copy",
        exact: true,
      }),
    ).toHaveCount(0);
    await expect(
      session.panel.getByRole("heading", { name: "Enemy attacker" }),
    ).toBeVisible();
  });
}

test("form state survives selection, zone and permission changes but resets after close", async ({
  page,
  mockTable,
}) => {
  const view = fixture();
  const { panel, update, command } = await inspector(mockTable, view);
  await panel.getByLabel("Card counter", { exact: true }).fill("charge");
  await panel.getByLabel("Transfer control").selectOption("1");
  await panel.getByLabel("Attach to").selectOption("attacker");
  await panel.getByLabel("Block attacker").selectOption("attacker");
  await panel.getByLabel("Inspect another card").selectOption("second");
  await expect(
    panel.getByRole("heading", { name: "Second card", exact: true }),
  ).toBeVisible();
  await expect(panel.getByLabel("Card counter", { exact: true })).toHaveValue(
    "charge",
  );
  await command(
    click(panel, "+ Counter"),
    "CARD_COUNTER",
    { name: "charge", delta: 1 },
    "second",
  );
  await update({
    ...view,
    cards: view.cards.map((c) =>
      c.id === "second" ? { ...c, zone: "graveyard" } : c,
    ),
  });
  await expect(panel.getByLabel("Transfer control")).toHaveCount(0);
  await update({
    ...view,
    cards: view.cards.map((c) =>
      c.id === "second" ? { ...c, editable: false } : c,
    ),
  });
  await expect(panel.getByLabel("Card counter", { exact: true })).toHaveCount(
    0,
  );
  await update(view);
  await expect(panel.getByLabel("Transfer control")).toHaveValue("1");
  await expect(panel.getByLabel("Attach to")).toHaveValue("attacker");
  await expect(panel.getByLabel("Block attacker")).toHaveValue("attacker");
  await panel.getByRole("button", { name: "Close inspector" }).click();
  await page
    .getByRole("button", { name: "Inspect Second card", exact: true })
    .click();
  await expect(
    panel.getByRole("heading", { name: "Second card", exact: true }),
  ).toBeVisible();
  await expect(panel.getByLabel("Card counter", { exact: true })).toHaveValue(
    "+1/+1",
  );
  await expect(panel.getByLabel("Transfer control")).toHaveValue("0");
  await expect(panel.getByLabel("Attach to")).toHaveValue("");
  await expect(panel.getByLabel("Block attacker")).toHaveValue("");
});

test("hidden and multi-face cards preserve presentation and current snapshot actions", async ({
  mockTable,
}) => {
  const view = fixture();
  const { panel, update, command } = await inspector(mockTable, view);
  await expect(
    panel.getByRole("button", { name: "Other face", exact: true }),
  ).toHaveCount(0);
  const definition = view.cards[0].definition!;
  await update({
    ...view,
    cards: view.cards.map((c) =>
      c.id === "card"
        ? {
            ...c,
            face: 1,
            definition: {
              ...definition,
              faces: [
                ...definition.faces,
                {
                  name: "Back",
                  image:
                    "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
                  text: "Back rules",
                  type: "Land",
                },
              ],
            },
          }
        : c,
    ),
  });
  await expect(
    panel.getByRole("heading", { name: "Back", exact: true }),
  ).toBeVisible();
  await expect(panel.locator(".rules-text")).toHaveText("Back rules");
  await expect(panel.getByRole("img")).toHaveAttribute("alt", "Back");
  await command(click(panel, "Other face"), "TRANSFORM");
  await update({
    ...view,
    cards: view.cards.map((c) =>
      c.id === "card"
        ? {
            ...c,
            face: 10,
            definition: { ...definition, text: "Fallback rules", image: "" },
          }
        : c,
    ),
  });
  await expect(
    panel.getByRole("heading", { name: "Private card name", exact: true }),
  ).toBeVisible();
  await expect(panel.locator(".rules-text")).toHaveText("Fallback rules");
  await panel.getByLabel("Inspect another card").selectOption("hidden");
  await expect(
    panel.getByRole("heading", { name: "Face-down card", exact: true }),
  ).toBeVisible();
  await expect(panel.locator(".rules-text")).toHaveText(
    "This card’s identity is hidden.",
  );
  await expect(panel.getByRole("img")).toHaveCount(0);
  await expect(
    panel.getByRole("button", { name: "Create token copy", exact: true }),
  ).toHaveCount(0);
  await expect(
    panel.getByRole("button", { name: "Tap", exact: true }),
  ).toHaveCount(0);
});

test("token editing preserves prompt defaults, name cancellation and later cancellation", async ({
  page,
  mockTable,
}) => {
  const view = fixture();
  view.cards[0].token = true;
  const { panel, command, commands } = await inspector(mockTable, view);
  await expect(
    panel.getByRole("button", {
      name: "Edit token characteristics",
      exact: true,
    }),
  ).toBeVisible();
  const prompts: string[] = [];
  const cancelName = async (dialog: import("@playwright/test").Dialog) => {
    prompts.push(dialog.defaultValue());
    await dialog.dismiss();
  };
  page.on("dialog", cancelName);
  await panel
    .getByRole("button", { name: "Edit token characteristics", exact: true })
    .click();
  expect(commands).toHaveLength(0);
  expect(prompts).toEqual(["Private card name"]);
  page.off("dialog", cancelName);
  let step = 0;
  const edit = async (dialog: import("@playwright/test").Dialog) => {
    if (step++ === 0) await dialog.accept("New token");
    else await dialog.dismiss();
  };
  page.on("dialog", edit);
  await command(click(panel, "Edit token characteristics"), "EDIT_TOKEN", {
    name: "New token",
    type: "",
    text: "",
  });
  page.off("dialog", edit);
  let promptIndex = 0;
  const answers = ["Named token", "Artifact", "Token rules"];
  page.on("dialog", async (dialog) => {
    prompts.push(dialog.defaultValue());
    await dialog.accept(answers[promptIndex++]);
  });
  await command(click(panel, "Edit token characteristics"), "EDIT_TOKEN", {
    name: "Named token",
    type: "Artifact",
    text: "Token rules",
  });
  expect(prompts.slice(1)).toEqual(["Private card name", "Creature", ""]);
});
