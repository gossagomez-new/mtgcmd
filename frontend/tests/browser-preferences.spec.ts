import { z } from "zod";
import { test, expect, roomId } from "./support/tableFixture";
import { snapshotFixture } from "../src/protocolFixtures";
import type { GameFormat } from "../src/protocol";

for (const format of ["COMMANDER", "STANDARD"] satisfies GameFormat[]) {
  for (const join of [false, true]) {
    for (const failure of ["write", "getter", "read", "unavailable"] as const) {
      test(`${format}: ${join ? "join" : "create"} accepts success despite ${failure} preference failure`, async ({
        page,
        mockTable,
      }) => {
        const view = {
          ...snapshotFixture(format),
          id: roomId,
          status: "lobby",
        };
        await mockTable.install(view);
        await page.addInitScript((failure) => {
          if (failure === "write") {
            Storage.prototype.setItem = () => {
              throw new DOMException("Preference denied", "QuotaExceededError");
            };
          } else if (failure === "read") {
            Storage.prototype.getItem = () => {
              throw new Error("Read denied");
            };
          } else {
            Object.defineProperty(window, "localStorage", {
              get: () => {
                if (failure === "getter") throw new Error("Access denied");
                return undefined;
              },
            });
          }
        }, failure);
        const capture = await mockTable.captureJson(
          join ? `**/api/rooms/${roomId}/join` : "**/api/rooms",
          join
            ? z.object({ name: z.literal("Player") }).strict()
            : z
                .object({
                  name: z.literal("Player"),
                  format: z.literal(format),
                })
                .strict(),
          { body: view },
        );
        if (join) {
          await page.route(`**/api/rooms/${roomId}`, (route) =>
            route.fulfill({ status: 403, json: { message: "Not seated" } }),
          );
        }
        await page.goto(join ? `/room/${roomId}` : "/");
        await page.getByRole("textbox", { name: "Your name" }).fill("Player");
        if (!join) await page.getByLabel("Table format").selectOption(format);
        await page
          .getByRole("button", { name: join ? "Join table" : "Create table" })
          .click();
        await expect(page.locator("main.lobby")).toBeVisible();
        await expect(
          page.getByText("Connected", { exact: true }),
        ).toBeVisible();
        await expect(page).toHaveURL(`/room/${roomId}`);
        await expect(page.getByRole("alert")).toHaveCount(0);
        expect(capture.requests).toHaveLength(1);
        await page.evaluate(() => {
          history.pushState({}, "", "/");
          dispatchEvent(new PopStateEvent("popstate"));
        });
        await expect(
          page.getByRole("textbox", { name: "Your name" }),
        ).toHaveValue("Player");
        await expect(
          page.getByRole("button", { name: "Create table" }),
        ).toBeEnabled();
        expect(capture.requests).toHaveLength(1);
      });
    }
  }
}

for (const failure of ["getter", "read", "unavailable"] as const) {
  test(`renders and retains typed name with ${failure} storage failure`, async ({
    page,
    mockTable,
  }) => {
    await mockTable.install();
    await page.addInitScript((failure) => {
      if (failure === "read") {
        Storage.prototype.getItem = () => {
          throw new Error("Read denied");
        };
      } else {
        Object.defineProperty(window, "localStorage", {
          get: () => {
            if (failure === "getter") throw new Error("Access denied");
            return undefined;
          },
        });
      }
    }, failure);
    await page.goto("/");
    const name = page.getByRole("textbox", { name: "Your name" });
    await expect(name).toHaveValue("");
    await name.fill("Temporary name");
    await page.getByLabel("Table format").selectOption("STANDARD");
    await expect(name).toHaveValue("Temporary name");
    await page.reload();
    await expect(name).toHaveValue("");
  });
}

test("working storage restores and saves the existing preference across reload", async ({
  page,
  mockTable,
}) => {
  const view = { ...snapshotFixture(), id: roomId, status: "lobby" };
  await mockTable.install(view);
  await mockTable.captureJson(
    "**/api/rooms",
    z.object({ name: z.string(), format: z.string() }),
    { body: view },
  );
  await page.goto("/");
  await expect(page.getByRole("textbox", { name: "Your name" })).toHaveValue(
    "",
  );
  await page.getByRole("textbox", { name: "Your name" }).fill("Remembered");
  await page.getByRole("button", { name: "Create table" }).click();
  await expect(page.locator("main.lobby")).toBeVisible();
  await page.goto("/");
  await page.reload();
  await expect(page.getByRole("textbox", { name: "Your name" })).toHaveValue(
    "Remembered",
  );
});

test("API failure remains visible without navigation, preference writes or retries", async ({
  page,
  mockTable,
}) => {
  await mockTable.install();
  await page.addInitScript(() => localStorage.setItem("table-name", "Saved"));
  const capture = await mockTable.captureJson(
    "**/api/rooms",
    z.object({ name: z.string(), format: z.string() }),
    { status: 400, body: { message: "Room creation rejected" } },
  );
  await page.goto("/");
  await page.getByRole("textbox", { name: "Your name" }).fill("Attempted");
  await page.getByRole("button", { name: "Create table" }).click();
  await expect(page.getByRole("alert")).toContainText("Room creation rejected");
  await expect(
    page.getByRole("button", { name: "Create table" }),
  ).toBeEnabled();
  await expect(page).toHaveURL("/");
  expect(capture.requests).toHaveLength(1);
  expect(await page.evaluate(() => localStorage.getItem("table-name"))).toBe(
    "Saved",
  );
  await expect(page.getByRole("textbox", { name: "Your name" })).toHaveValue(
    "Attempted",
  );
});
