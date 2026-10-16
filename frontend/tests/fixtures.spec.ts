import { z } from "zod";
import { snapshotFixture } from "../src/protocolFixtures";
import { test, expect, TableFixture, roomId } from "./support/tableFixture";
import { decklist, commander, secondCommander } from "./support/selectors";
const viewFixture = () => ({ ...snapshotFixture("STANDARD"), id: roomId });

test("snapshot updates preserve pending commands until an explicit acknowledgment", async ({
  page,
  mockTable,
}) => {
  const view = viewFixture();
  await mockTable.open(view);
  const next = page.getByRole("button", { name: /Next phase/ });
  await next.click();
  await expect.poll(() => mockTable.commands.length).toBe(1);
  expect(mockTable.commands[0]).toEqual({
    id: expect.any(String),
    revision: 7,
    type: "PHASE",
    data: {},
  });
  await mockTable.snapshot(
    { ...view, revision: 8, phase: "Upkeep" },
    async () => {
      await expect(page.locator(".turn-strip")).toContainText("Upkeep");
    },
  );
  await expect(next).toBeDisabled();
  await mockTable.acknowledge(
    { ...view, revision: 9, phase: "Main phase" },
    mockTable.commands[0].id,
    async () => {
      await expect(page.locator(".turn-strip")).toContainText("Main phase");
      await expect(next).toBeEnabled();
    },
  );
  expect(mockTable.counts()).toEqual({ sessions: 1, connections: 1 });
});

test("delivery waits for the scenario's observation and fixtures start isolated", async ({
  page,
  mockTable,
}) => {
  const view = viewFixture();
  await mockTable.open(view);
  expect(mockTable.commands).toEqual([]);
  let release = () => {};
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  let completed = false;
  const delivery = mockTable
    .snapshot({ ...view, revision: 8, phase: "Observed phase" }, async () => {
      await gate;
      await expect(page.locator(".turn-strip")).toContainText("Observed phase");
    })
    .then(() => {
      completed = true;
    });
  try {
    await expect(page.locator(".turn-strip")).toContainText("Observed phase");
    expect(completed).toBe(false);
  } finally {
    release();
    await delivery;
  }
  expect(completed).toBe(true);
});

test("typed request queues use fallbacks and dispose unresolved replies", async ({
  page,
  mockTable,
}) => {
  await mockTable.open(viewFixture());
  await page.route("**/api/probe", (route) =>
    route.fulfill({ json: { result: "GET fallback" } }),
  );
  const endpoint = await mockTable.captureJson(
    "**/api/probe",
    z.object({ text: z.string() }),
    { body: { result: "fallback" } },
  );
  const getBody: unknown = await page.evaluate(async () => {
    const response = await fetch("/api/probe");
    return (await response.json()) as unknown;
  });
  expect(getBody).toEqual({ result: "GET fallback" });
  expect(endpoint.requests).toEqual([]);
  const fetchProbe = () =>
    page.evaluate(async () => {
      const response = await fetch("/api/probe", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: "payload" }),
      });
      const body: unknown = await response.json();
      return { status: response.status, body };
    });
  expect(await fetchProbe()).toEqual({
    status: 200,
    body: { result: "fallback" },
  });
  endpoint.replies.push({ status: 201, body: { result: "queued" } });
  expect(await fetchProbe()).toEqual({
    status: 201,
    body: { result: "queued" },
  });
  const pending = endpoint.defer();
  const response = fetchProbe();
  await expect.poll(() => endpoint.requests.length).toBe(3);
  expect(endpoint.requests).toEqual([
    { text: "payload" },
    { text: "payload" },
    { text: "payload" },
  ]);
  expect(mockTable.pendingResponses).toBe(1);
  await mockTable.dispose();
  expect((await response).status).toBe(503);
  expect(mockTable.pendingResponses).toBe(0);
  pending.resolve({ body: "late resolution" });
  await mockTable.dispose();
});

test("malformed captured requests abort and surface fixture failure at cleanup", async ({
  page,
}) => {
  const table = new TableFixture(page);
  await table.open(viewFixture());
  const endpoint = await table.captureJson(
    "**/api/probe",
    z.object({ text: z.string() }),
    { body: {} },
  );
  const result = await page.evaluate(async () => {
    try {
      await fetch("/api/probe", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: '{"text":1}',
      });
      return "unexpected success";
    } catch {
      return "aborted";
    }
  });
  expect(result).toBe("aborted");
  expect(endpoint.requests).toEqual([]);
  await expect(table.dispose()).rejects.toThrow(/fixture handler/i);
});

test("startup rejects invalid snapshots before connecting and releases failed setup", async ({
  page,
}) => {
  const table = new TableFixture(page);
  await expect(
    table.open({ ...viewFixture(), id: "invalid-room" }),
  ).rejects.toThrow(/snapshot|UUID/i);
  expect(table.counts()).toEqual({ sessions: 0, connections: 0 });
  await table.dispose();
  expect(() => table.sendRaw("{}")).toThrow();
});

test("closed-page startup fails without masking the original error", async ({
  page,
}) => {
  const table = new TableFixture(page);
  await page.close();
  await expect(table.open(viewFixture())).rejects.toThrow(/closed/i);
  await table.dispose();
});

test("selectors survive textarea content and changing commander options", async ({
  page,
}) => {
  await page.setContent(
    '<label>Decklist<textarea></textarea></label><label>Commander<select><option value="a">First</option></select></label><label>Second commander (optional)<select><option value="">None</option><option value="b">Second</option></select></label>',
  );
  const text = decklist(page);
  const primary = commander(page);
  const secondary = secondCommander(page);
  await text.fill("Deck\n1 Card");
  await secondary.selectOption("b");
  await expect(text).toHaveValue("Deck\n1 Card");
  await expect(secondary).toHaveValue("b");
  await page.evaluate(() => {
    const textarea = document.querySelector("textarea")!;
    textarea.defaultValue = "Deck\n1 Card";
    const select = document.querySelector("select")!;
    select.add(new Option("New choice", "c"));
  });
  await primary.selectOption("c");
  await expect(primary).toHaveValue("c");
  await text.fill("Changed deck");
  await expect(text).toHaveValue("Changed deck");
});
