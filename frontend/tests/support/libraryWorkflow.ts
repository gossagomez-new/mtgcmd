import { expect, type Page } from "@playwright/test";
import { readSnapshot } from "./tabletopNetwork";

/** Real-server privacy, staged scry and top-card actions shared by both formats. */
export async function exerciseLibraryWorkflow(actor: Page, observer: Page) {
  const url = new URL(
    `/api/rooms/${new URL(actor.url()).pathname.split("/").pop()}`,
    actor.url(),
  ).toString();
  const before = await readSnapshot(actor.request, url);
  const count = actor.getByRole("spinbutton", { name: "Number of top cards" });
  await count.fill("3");
  await actor.getByRole("button", { name: "Scry", exact: true }).click();
  const dialog = actor.getByRole("dialog", { name: "Scry library" });
  await expect(dialog).toBeVisible();
  const opened = await readSnapshot(actor.request, url);
  const session = opened.libraryInspection;
  expect(session).toBeTruthy();
  if (!session) throw new Error("Server did not persist the scry selection");
  expect(session.cardIds).toHaveLength(3);
  const other = await readSnapshot(observer.request, url);
  expect(other.libraryInspection).toBeNull();
  for (const id of session.cardIds)
    expect(other.cards.some((card) => card.id === id)).toBe(false);
  await actor.reload();
  await expect(actor.getByText("Connected", { exact: true })).toBeVisible({
    timeout: 30000,
  });
  await expect(dialog).toBeVisible();
  const resumed = await readSnapshot(actor.request, url);
  expect(resumed.revision).toBe(opened.revision);
  expect(resumed.libraryInspection).toEqual(session);
  const first = resumed.cards.find((card) => card.id === session.cardIds[0]);
  const name =
    first?.definition?.faces[first.face]?.name ||
    first?.definition?.name ||
    "Face-down card";
  await dialog
    .getByRole("button", { name: `Move ${name} to bottom`, exact: true })
    .first()
    .click();
  expect((await readSnapshot(actor.request, url)).revision).toBe(
    resumed.revision,
  );
  await dialog
    .getByRole("button", { name: "Confirm scry", exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  const confirmed = await readSnapshot(actor.request, url);
  expect(confirmed.libraryInspection).toBeNull();
  expect(confirmed.players[confirmed.you].counts).toEqual(
    before.players[before.you].counts,
  );
  await count.fill("2");
  await actor.getByRole("button", { name: "Look at top", exact: true }).click();
  const look = actor.getByRole("dialog", { name: "Look at top cards" });
  await expect(look).toBeVisible();
  await look
    .getByRole("button", { name: "Close inspection", exact: true })
    .click();
  await expect(look).toHaveCount(0);
  const previous = await readSnapshot(observer.request, url);
  const known = new Set(previous.cards.map((card) => card.id));
  await count.fill("1");
  await actor.getByRole("button", { name: "Exile top", exact: true }).click();
  await expect
    .poll(
      async () =>
        (await readSnapshot(actor.request, url)).players[before.you].counts
          .library,
    )
    .toBe(before.players[before.you].counts.library - 1);
  const exiled = (await readSnapshot(observer.request, url)).cards.filter(
    (card) => card.zone === "exile" && !known.has(card.id),
  );
  expect(exiled).toHaveLength(1);
  expect(exiled[0].faceDown).toBe(false);
  expect(exiled[0].definition).toBeTruthy();
}
