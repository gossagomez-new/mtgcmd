import {
  test,
  expect,
  roomId,
  type TableFixture,
} from "./support/tableFixture";
import { snapshotFixture, definitionFixture } from "../src/protocolFixtures";
import type { Card, GameFormat } from "../src/protocol";

const front =
  'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="280"/>';
const back =
  'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="400" height="560"/>';
async function open(table: TableFixture, format: GameFormat) {
  const view = { ...snapshotFixture(format), id: roomId };
  view.cards = [
    {
      ...view.cards[0],
      definition: {
        ...definitionFixture(),
        image: front,
        faces: [
          { name: "Front", image: front, type: "Creature", text: "" },
          { name: "Back", image: back, type: "Creature", text: "" },
        ],
      },
    },
  ];
  await table.open(view);
  return table.page.getByRole("button", { name: "Inspect Front", exact: true });
}
async function update(table: TableFixture, changes: Partial<Card>) {
  const revision = table.view.revision + 1;
  await table.snapshot(
    {
      ...table.view,
      revision,
      phase: `Preview ${revision}`,
      cards: table.view.cards.map((card) => ({ ...card, ...changes })),
    },
    async () => {
      await expect(table.page.locator(".turn-strip")).toContainText(
        `Preview ${revision}`,
      );
    },
  );
}
for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: hover preview is delayed, enlarged, hoverable and dismissible`, async ({
    mockTable,
    page,
  }) => {
    const card = await open(mockTable, format);
    const preview = page.getByRole("tooltip");
    await page.clock.install();
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 1000));
    await card.hover();
    await page.clock.fastForward(349);
    await expect(preview).toHaveCount(0);
    await page.clock.fastForward(1);
    await expect(preview).toBeVisible();
    await expect(preview).toHaveAccessibleName("Artwork: Front");
    await expect(card).toHaveAttribute(
      "aria-describedby",
      (await preview.getAttribute("id")) ?? "",
    );
    const source = await card.boundingBox();
    const box = await preview.boundingBox();
    expect(box!.width).toBeGreaterThan(source!.width);
    expect(box!.height).toBeGreaterThan(source!.height);
    expect(
      box!.x + box!.width <= source!.x ||
        box!.x >= source!.x + source!.width ||
        box!.y + box!.height <= source!.y ||
        box!.y >= source!.y + source!.height,
    ).toBe(true);
    await preview.hover();
    await page.clock.fastForward(300);
    await expect(preview).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(preview).toHaveCount(0);
    await page.clock.fastForward(500);
    await expect(preview).toHaveCount(0);
    expect(mockTable.commands).toEqual([]);
  });
  test(`${format}: focus, Escape and short hover cancellation preserve inspection`, async ({
    mockTable,
    page,
  }) => {
    const card = await open(mockTable, format);
    await page.clock.install();
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 1000));
    await card.hover();
    await page.mouse.move(1, 1);
    await page.clock.fastForward(500);
    await expect(page.getByRole("tooltip")).toHaveCount(0);
    await card.focus();
    await page.clock.fastForward(350);
    await expect(page.getByRole("tooltip")).toBeVisible();
    await expect(card).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(card).toBeFocused();
    await expect(page.getByRole("tooltip")).toHaveCount(0);
    await page.keyboard.press("Enter");
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toBeVisible();
    await expect(page.getByRole("tooltip")).toHaveCount(0);
  });
  test(`${format}: current faces, fallbacks and hidden artwork follow snapshots`, async ({
    mockTable,
    page,
  }) => {
    let card = await open(mockTable, format);
    await card.hover();
    const preview = page.getByRole("tooltip");
    await expect(preview.locator("img")).toHaveAttribute("src", front);
    await update(mockTable, { face: 1 });
    await expect(preview.locator("img")).toHaveAttribute("src", back);
    await expect(preview).toHaveAccessibleName("Artwork: Back");
    await update(mockTable, { face: 99 });
    await expect(preview.locator("img")).toHaveAttribute("src", front);
    await update(mockTable, {
      face: 0,
      definition: {
        ...mockTable.view.cards[0].definition!,
        faces: [{ name: "", image: "", type: "", text: "" }],
      },
    });
    await expect(preview.locator("img")).toHaveAttribute("src", front);
    await update(mockTable, { faceDown: true });
    await page.mouse.move(1, 1);
    await page.getByTestId("your-battlefield").locator(".card").hover();
    await expect(preview.locator("img")).toHaveAttribute("src", front);
    await update(mockTable, { definition: undefined });
    await expect(preview).toHaveCount(0);
    card = page.getByRole("button", {
      name: "Inspect Face-down card",
      exact: true,
    });
    await card.focus();
    await page.waitForTimeout(450);
    await expect(preview).toHaveCount(0);
    await update(mockTable, {
      definition: { ...definitionFixture(), image: "", faces: [] },
    });
    await page.getByTestId("your-battlefield").locator(".card").hover();
    await page.waitForTimeout(450);
    await expect(preview).toHaveCount(0);
  });
  test(`${format}: menu, tap and dragging dismiss without extra commands`, async ({
    mockTable,
    page,
  }) => {
    const card = await open(mockTable, format);
    const preview = page.getByRole("tooltip");
    await card.hover();
    await expect(preview).toBeVisible();
    await card.click({ button: "right" });
    await expect(
      page.getByRole("menu", { name: "Card actions" }),
    ).toBeVisible();
    await expect(preview).toHaveCount(0);
    await page.keyboard.press("Escape");
    await page.mouse.move(1, 1);
    await card.hover();
    await expect(preview).toBeVisible();
    await card.dblclick();
    await expect(preview).toHaveCount(0);
    await expect.poll(() => mockTable.commands.length).toBe(1);
    expect(mockTable.commands[0].type).toBe("TAP");
    await page.mouse.move(1, 1);
    await card.hover();
    await expect(preview).toBeVisible();
    await card.dispatchEvent("dragstart", {
      dataTransfer: await page.evaluateHandle(() => new DataTransfer()),
    });
    await expect(preview).toHaveCount(0);
    await expect(
      page.getByRole("dialog", { name: "Card inspector" }),
    ).toHaveCount(0);
    expect(mockTable.commands).toHaveLength(1);
  });
  test(`${format}: viewport edges, removal and failed artwork clean up previews`, async ({
    mockTable,
    page,
  }) => {
    const card = await open(mockTable, format);
    await page.setViewportSize({ width: 390, height: 780 });
    await card.scrollIntoViewIfNeeded();
    await card.hover();
    const preview = page.getByRole("tooltip");
    await expect(preview).toBeVisible();
    let box = await preview.boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(8);
    expect(box!.y).toBeGreaterThanOrEqual(8);
    expect(box!.x + box!.width).toBeLessThanOrEqual(382);
    expect(box!.y + box!.height).toBeLessThanOrEqual(772);
    await page.setViewportSize({ width: 500, height: 850 });
    box = await preview.boundingBox();
    expect(box!.x + box!.width).toBeLessThanOrEqual(492);
    await preview.locator("img").dispatchEvent("error");
    await expect(preview).toHaveCount(0);
    await page.mouse.move(1, 1);
    await card.hover();
    await expect(preview).toBeVisible();
    await mockTable.snapshot(
      { ...mockTable.view, revision: mockTable.view.revision + 1, cards: [] },
      async () => {
        await expect(card).toHaveCount(0);
      },
    );
    await expect(preview).toHaveCount(0);
  });
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: focused previews dismiss before their parent dialog`, async ({
    mockTable,
    page,
  }) => {
    await open(mockTable, format);
    const player = {
      ...mockTable.view.players[0],
      seat: 1,
      name: "Bob",
      privateAccess: false,
    };
    await mockTable.snapshot(
      {
        ...mockTable.view,
        revision: mockTable.view.revision + 1,
        players: [...mockTable.view.players, player],
        cards: [
          {
            ...mockTable.view.cards[0],
            controller: 1,
            owner: 1,
            zoneOwner: 1,
            editable: false,
          },
        ],
      },
      async () => {
        await expect(
          page.getByRole("button", { name: "Focus Bob battlefield" }),
        ).toBeVisible();
      },
    );
    await page.getByRole("button", { name: "Focus Bob battlefield" }).click();
    const dialog = page.getByRole("dialog", { name: "Bob battlefield" });
    await expect(dialog).toBeVisible();
    const card = dialog.getByRole("button", {
      name: "Inspect Front",
      exact: true,
    });
    await card.focus();
    const preview = page.getByRole("tooltip");
    await expect(preview).toBeVisible();
    expect((await preview.boundingBox())!.width).toBeGreaterThan(
      (await card.boundingBox())!.width,
    );
    await page.keyboard.press("Escape");
    await expect(preview).toHaveCount(0);
    await expect(dialog).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(dialog).toHaveCount(0);
  });
  test(`${format}: actions cancel delayed opening and pointer exit closes a preview`, async ({
    mockTable,
    page,
  }) => {
    const card = await open(mockTable, format);
    await page.clock.install();
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 1000));
    await card.focus();
    await page.clock.fastForward(100);
    await page.getByRole("button", { name: "Actions for Front" }).click();
    await page.clock.fastForward(500);
    await expect(page.getByRole("tooltip")).toHaveCount(0);
    await expect(
      page.getByRole("menu", { name: "Card actions" }),
    ).toBeVisible();
    await page.keyboard.press("Escape");
    await page.mouse.move(1, 1);
    await card.hover();
    await page.clock.fastForward(350);
    const preview = page.getByRole("tooltip");
    await expect(preview).toBeVisible();
    await preview.hover();
    await page.mouse.move(1, 1);
    await page.clock.fastForward(200);
    await expect(preview).toHaveCount(0);
  });
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: Escape and disappearance cancel pending previews`, async ({
    mockTable,
    page,
  }) => {
    const card = await open(mockTable, format);
    await page.clock.install();
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 1000));
    await card.hover();
    await page.clock.fastForward(100);
    await page.keyboard.press("Escape");
    await page.clock.fastForward(500);
    await expect(page.getByRole("tooltip")).toHaveCount(0);
    await page.mouse.move(1, 1);
    await card.hover();
    await page.clock.fastForward(100);
    await mockTable.snapshot(
      { ...mockTable.view, revision: mockTable.view.revision + 1, cards: [] },
      async () => {
        await expect(card).toHaveCount(0);
      },
    );
    await page.clock.fastForward(500);
    await expect(page.getByRole("tooltip")).toHaveCount(0);
    expect(mockTable.commands).toHaveLength(0);
  });
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: preview follows a tapped snapshot without covering its card`, async ({
    mockTable,
    page,
  }) => {
    const card = await open(mockTable, format);
    await card.hover();
    const preview = page.getByRole("tooltip");
    await expect(preview).toBeVisible();
    await update(mockTable, { tapped: true });
    await expect
      .poll(() =>
        card.evaluate((element) => {
          const transform = new DOMMatrixReadOnly(
            getComputedStyle(element).transform,
          );
          return Math.round(
            (Math.atan2(transform.b, transform.a) * 180) / Math.PI,
          );
        }),
      )
      .toBe(90);
    await expect(preview).toBeVisible();
    await expect
      .poll(async () => {
        const source = (await card.boundingBox())!;
        const box = (await preview.boundingBox())!;
        return (
          box.x >= source.x + source.width ||
          box.x + box.width <= source.x ||
          box.y >= source.y + source.height ||
          box.y + box.height <= source.y
        );
      })
      .toBe(true);
    expect(
      await preview.evaluate((element) => getComputedStyle(element).transform),
    ).toBe("none");
  });
}

for (const format of ["STANDARD", "COMMANDER"] as const) {
  test(`${format}: previews below a card leave its Actions button accessible`, async ({
    mockTable,
    page,
  }) => {
    const card = await open(mockTable, format);
    await page.setViewportSize({ width: 280, height: 1500 });
    await page
      .getByTestId("your-battlefield")
      .locator(".card-interaction")
      .evaluate((element) => {
        Object.assign((element as HTMLElement).style, {
          position: "fixed",
          left: "85px",
          top: "600px",
        });
      });
    await card.hover();
    const preview = page.getByRole("tooltip");
    await expect(preview).toBeVisible();
    const actions = page.getByRole("button", { name: "Actions for Front" });
    const controls = (await actions.boundingBox())!;
    const box = (await preview.boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(controls.y + controls.height);
    await actions.click({ timeout: 2000 });
    await expect(
      page.getByRole("menu", { name: "Card actions" }),
    ).toBeVisible();
    await expect(preview).toHaveCount(0);
  });
}
