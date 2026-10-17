import { test, expect, type TableFixture } from "./support/tableFixture";
import type { Locator } from "@playwright/test";
import { snapshotFixture, definitionFixture } from "../src/protocolFixtures";
import type { Card } from "../src/protocol";
import { roomId } from "./support/tableFixture";

const frontImage =
  'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"/>';
const backImage =
  'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"/>';

async function open(mockTable: TableFixture) {
  const view = { ...snapshotFixture("STANDARD"), id: roomId };
  view.cards = [{ ...view.cards[0], counters: {} }];
  await mockTable.open(view);
  return mockTable.page.getByTestId("your-battlefield").locator(".card");
}

async function update(mockTable: TableFixture, changes: Partial<Card>) {
  const revision = mockTable.view.revision + 1;
  const phase = `Card fixture ${revision}`;
  await mockTable.snapshot(
    {
      ...mockTable.view,
      revision,
      phase,
      cards: mockTable.view.cards.map((card) => ({ ...card, ...changes })),
    },
    async () => {
      await expect(mockTable.page.locator(".turn-strip")).toContainText(phase);
    },
  );
}

async function rotation(card: Locator, degrees: number) {
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

test("selected faces and definition fallbacks preserve artwork, names and fallback text", async ({
  mockTable,
}) => {
  const card = await open(mockTable);
  const definition = {
    ...definitionFixture(),
    name: "Whole card",
    image: frontImage,
    type: "Definition type",
    text: "Definition rules",
    faces: [
      {
        name: "Front face",
        image: frontImage,
        type: "Front type",
        text: "Front rules",
      },
      {
        name: "Back face",
        image: backImage,
        type: "Back type",
        text: "Back rules",
      },
      { name: "", image: "", type: "", text: "" },
    ],
  };
  for (const [face, name, image] of [
    [0, "Front face", frontImage],
    [1, "Back face", backImage],
    [2, "Whole card", frontImage],
    [99, "Whole card", frontImage],
  ] as const) {
    await update(mockTable, { definition, face });
    await expect(card).toHaveAccessibleName(`Inspect ${name}`);
    await expect(card.locator(".card-caption")).toHaveText(name);
    await expect(card.locator("img")).toHaveAttribute("src", image);
    await expect(card.locator("img")).toHaveAttribute("alt", name);
    await expect(card.locator("img")).toHaveAttribute("loading", "lazy");
    await expect(card.locator(".card-fallback")).toHaveCount(0);
  }
  await update(mockTable, {
    face: 1,
    definition: {
      ...definition,
      image: "",
      faces: definition.faces.map((face) => ({ ...face, image: "" })),
    },
  });
  await expect(card.locator("img")).toHaveCount(0);
  await expect(card.locator(".card-fallback strong")).toHaveText("Back face");
  await expect(card.locator(".card-fallback small")).toHaveText(
    "Definition type",
  );
  await expect(card.locator(".card-fallback p")).toHaveText("Definition rules");
});

test("missing definitions and empty strings keep placeholders while authorized face-down cards retain identity", async ({
  mockTable,
}) => {
  const card = await open(mockTable);
  await update(mockTable, { definition: undefined, faceDown: true });
  await expect(card).toHaveAccessibleName("Inspect Face-down card");
  await expect(card.locator(".card-fallback small")).toHaveText(
    "Hidden identity",
  );
  await expect(card.locator(".card-fallback p")).toBeEmpty();
  await expect(card.locator("img")).toHaveCount(0);
  await update(mockTable, {
    definition: {
      ...definitionFixture(),
      name: "",
      type: "",
      text: "",
      faces: [],
    },
  });
  await expect(card).toHaveAccessibleName("Inspect Face-down card");
  await expect(card.locator(".card-fallback small")).toHaveText(
    "Hidden identity",
  );
  await update(mockTable, { definition: definitionFixture() });
  await expect(card).toHaveAccessibleName("Inspect Front");
  await expect(card).toHaveClass(/face-down/);
  await expect(card.locator(".face-down-tag")).toHaveText("FACE DOWN");
});

test("all badge combinations retain order, classes and counter values", async ({
  mockTable,
}) => {
  const card = await open(mockTable);
  const classes = [
    "card-tag",
    "face-down-tag",
    "combat-tag",
    "counter-tag",
    "attachment-tag",
  ];
  const labels = [
    "TOKEN",
    "FACE DOWN",
    "Blocking",
    "charge: 0 · correction: -2",
    "Attached ↗",
  ];
  for (let mask = 0; mask < 32; mask++) {
    await update(mockTable, {
      token: Boolean(mask & 1),
      faceDown: Boolean(mask & 2),
      combat: mask & 4 ? "blocks:target" : "",
      counters: mask & 8 ? { charge: 0, correction: -2 } : {},
      attachedTo: mask & 16 ? "target" : null,
    });
    const active = labels.filter((_, index) => mask & (1 << index));
    await expect(card.locator(":scope > span")).toHaveText([
      "Front",
      ...active,
    ]);
    for (let index = 0; index < classes.length; index++) {
      await expect(card.locator(`.${classes[index]}`)).toHaveCount(
        mask & (1 << index) ? 1 : 0,
      );
    }
  }
  for (const combat of ["attacks", "Blocks:target", "other"]) {
    await update(mockTable, { combat, attachedTo: "" });
    await expect(card.locator(".combat-tag")).toHaveText("Attacking");
    await expect(card.locator(".attachment-tag")).toHaveCount(0);
  }
});

test("inspection follows current snapshots and dragging retains permissions and payload", async ({
  page,
  mockTable,
}) => {
  await open(mockTable);
  const card = page
    .locator(".card")
    .filter({ has: page.locator(".card-caption", { hasText: "Front" }) });
  for (const zone of ["battlefield", "hand"]) {
    for (const editable of [false, true]) {
      await update(mockTable, { zone, editable });
      await expect(card).toHaveAttribute(
        "draggable",
        String(editable && zone !== "battlefield"),
      );
    }
  }
  await page.evaluate(() => {
    document.addEventListener(
      "dragstart",
      (event) => {
        document.documentElement.dataset.dragId =
          event.dataTransfer?.getData("application/card");
        document.documentElement.dataset.dragEffect =
          event.dataTransfer?.effectAllowed;
      },
      { once: true },
    );
  });
  await card.dragTo(page.getByTestId("your-battlefield"));
  await expect(page.locator("html")).toHaveAttribute("data-drag-id", "card");
  await expect(page.locator("html")).toHaveAttribute(
    "data-drag-effect",
    "move",
  );
  await expect.poll(() => mockTable.commands.length).toBe(1);
  expect(mockTable.commands[0]).toMatchObject({
    type: "MOVE",
    data: { cardId: "card", zone: "battlefield" },
  });
  await card.click();
  const inspector = page.getByRole("dialog", { name: "Card inspector" });
  await expect(
    inspector.getByRole("heading", { name: "Front", exact: true }),
  ).toBeVisible();
  await update(mockTable, {
    face: 1,
    tapped: true,
    definition: {
      ...definitionFixture(),
      faces: [
        definitionFixture().faces[0],
        {
          name: "Updated back",
          image: "",
          type: "Artifact",
          text: "Updated rules",
        },
      ],
    },
  });
  await expect(
    inspector.getByRole("heading", { name: "Updated back", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Close inspector" }).click();
  await page
    .getByTestId("hand")
    .getByRole("button", { name: "Inspect Updated back", exact: true })
    .click();
  await expect(inspector).toContainText("Updated rules");
  await expect(inspector).toContainText("TAPPED");
});

test("both battlefield views retain 90-degree tap rotation through hover and snapshot updates", async ({
  page,
  mockTable,
}) => {
  const view = { ...snapshotFixture("STANDARD"), id: roomId };
  view.players.push({ ...view.players[0], seat: 1, name: "Bob" });
  view.cards = [
    view.cards[0],
    { ...view.cards[0], id: "opponent", owner: 1, controller: 1, zoneOwner: 1 },
  ];
  await mockTable.open(view);
  const cards = [
    page.getByTestId("your-battlefield").locator(".card"),
    page.getByTestId("opponent-1").locator(".card"),
  ];
  for (const tapped of [false, true, false]) {
    await update(mockTable, { tapped });
    for (const card of cards) {
      await rotation(card, tapped ? 90 : 0);
      await card.hover();
      await rotation(card, tapped ? 90 : 0);
    }
  }
});
