import { test, expect } from "@playwright/test";
import { snapshotFixture } from "../src/protocolFixtures";
import { ProtocolError } from "../src/protocol";
import {
  SnapshotObserver,
  parseImportRequest,
  readSnapshot,
  readSession,
  pollSnapshot,
  sendCommand,
  waitForFormat,
} from "./support/tabletopNetwork";

const view = snapshotFixture();

test("observer handles all message variants and preserves raw privacy evidence", () => {
  const observer = new SnapshotObserver();
  for (const message of [
    { type: "snapshot", view },
    { type: "ack", commandId: "one", view: { ...view, revision: 8 } },
    { type: "error", message: "rejected", view: { ...view, revision: 9 } },
  ]) {
    observer.receive(Buffer.from(JSON.stringify(message)));
    expect(observer.view?.revision).toBe(message.view.revision);
  }
  observer.receive(JSON.stringify({ type: "presence", online: [0] }));
  observer.receive(JSON.stringify({ type: "error", message: "rejected" }));
  observer.receive(JSON.stringify({ type: "error", message: null }));
  expect(observer.view?.revision).toBe(9);
  const raw = JSON.stringify({
    type: "snapshot",
    view: { ...view, unexpectedSecret: "SECRET" },
  });
  observer.receive(raw);
  expect(observer.view).not.toHaveProperty("unexpectedSecret");
  expect(observer.frames).toContain(raw);
  expect(observer.frames.join("\n")).toContain("SECRET");
  observer.assertHealthy();
});

test("invalid frames remain failures after valid traffic without exposing payloads", () => {
  for (const raw of [
    "SECRET",
    JSON.stringify({ type: "unknown" }),
    JSON.stringify({ type: "ack", view }),
    JSON.stringify({ type: "presence", online: ["wrong"] }),
    JSON.stringify({ type: "snapshot", view: { ...view, cards: [null] } }),
    JSON.stringify({ type: "snapshot", view: { secret: "SECRET" } }),
  ]) {
    const observer = new SnapshotObserver();
    observer.receive(raw);
    observer.receive(JSON.stringify({ type: "snapshot", view }));
    expect(() => observer.assertHealthy()).toThrow("Invalid server frame");
    expect(() => observer.assertHealthy()).not.toThrow(/SECRET/);
    expect(observer.view).toEqual(view);
  }
});

test("import request validates URL and pasted text fields", () => {
  for (const body of [
    { url: "https://moxfield.com/decks/test", text: "" },
    { url: "", text: "1 Card" },
  ]) {
    expect(parseImportRequest(body)).toEqual(body);
  }
  for (const body of [null, {}, { url: 2, text: "" }, { url: "", text: [] }]) {
    expect(() => parseImportRequest(body)).toThrow();
  }
});

test("HTTP success is decoded, status errors take precedence, and commands execute once", async ({
  request,
}) => {
  const bodies: unknown[] = [];
  const headers: unknown[] = [];
  const origins: unknown[] = [];
  const gets: string[] = [];
  const { createServer } = await import("node:http");
  let handlerFailure: unknown;
  let recoveries = 0;
  const server = createServer((req, res) => {
    void handle().catch((error: unknown) => {
      handlerFailure = error;
      res.statusCode = 500;
      res.end();
    });
    async function handle() {
      if (req.method === "POST") {
        let text = "";
        for await (const part of req) text += String(part);
        const body: unknown = JSON.parse(text);
        bodies.push(body);
        headers.push(req.headers["x-csrf-token"]);
        origins.push(req.headers.origin);
      } else {
        gets.push(req.url ?? "");
      }
      res.setHeader("Content-Type", "application/json");
      if (req.url === "/session")
        return res.end(JSON.stringify({ csrf: "token" }));
      if (req.url === "/bad-session") return res.end('{"csrf":3}');
      if (req.url === "/invalid" || req.url === "/bad-command/commands")
        return res.end('{"cards":[]}');
      if (req.url === "/syntax") return res.end("not-json");
      if (req.url === "/recover" && recoveries++ === 0) {
        res.statusCode = 503;
        return res.end("not-json");
      }
      if (req.url?.startsWith("/unavailable")) {
        res.statusCode = Number(req.url.split("/").pop());
        return res.end("not-json");
      }
      if (
        req.url === "/forbidden" ||
        req.url === "/forbidden-command/commands"
      ) {
        res.statusCode = 403;
        return res.end("not-json");
      }
      res.end(JSON.stringify(view));
    }
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  if (!address || typeof address === "string")
    throw new Error("Missing server address");
  const base = `http://127.0.0.1:${address.port}`;
  try {
    expect(await readSnapshot(request, base)).toEqual(view);
    expect(await readSession(request, `${base}/session`)).toEqual({
      csrf: "token",
    });
    await expect(
      readSession(request, `${base}/bad-session`),
    ).rejects.toBeInstanceOf(ProtocolError);
    await expect(
      readSnapshot(request, `${base}/invalid`),
    ).rejects.toBeInstanceOf(ProtocolError);
    await expect(readSnapshot(request, `${base}/forbidden`)).rejects.toThrow(
      "HTTP 403",
    );
    for (const status of [502, 503, 504]) {
      expect(
        await pollSnapshot(request, `${base}/unavailable/${status}`),
      ).toBeNull();
    }
    await waitForFormat(request, `${base}/recover`, "COMMANDER");
    expect(recoveries).toBe(2);
    await expect(pollSnapshot(request, `${base}/forbidden`)).rejects.toThrow(
      "HTTP 403",
    );
    await expect(
      pollSnapshot(request, `${base}/invalid`),
    ).rejects.toBeInstanceOf(ProtocolError);
    await expect(pollSnapshot(request, `${base}/syntax`)).rejects.toThrow();
    await expect(
      waitForFormat(request, `${base}/invalid`, "STANDARD"),
    ).rejects.toBeInstanceOf(ProtocolError);
    await expect(
      waitForFormat(request, `${base}/forbidden`, "STANDARD"),
    ).rejects.toThrow("HTTP 403");
    await waitForFormat(request, base, "COMMANDER");
    expect(
      await sendCommand(request, base, "token", "TOKEN", { name: "Elf" }),
    ).toEqual(view);
    await expect(
      sendCommand(request, `${base}/bad-command`, "token", "DRAW", {}),
    ).rejects.toBeInstanceOf(ProtocolError);
    await expect(
      sendCommand(request, `${base}/forbidden-command`, "token", "DRAW", {}),
    ).rejects.toThrow("HTTP 403");
    await expect(
      sendCommand(request, `${base}/invalid`, "token", "DRAW", {}),
    ).rejects.toBeInstanceOf(ProtocolError);
    expect(handlerFailure).toBeUndefined();
    expect(bodies).toHaveLength(3);
    expect(headers).toEqual(["token", "token", "token"]);
    expect(origins).toEqual([base, base, base]);
    expect(gets.filter((url) => url === "/invalid")).toHaveLength(4);
    expect(gets.filter((url) => url === "/forbidden")).toHaveLength(3);
    expect(bodies[0]).toMatchObject({
      revision: view.revision,
      type: "TOKEN",
      data: { name: "Elf" },
    });
    expect(bodies[0]).toHaveProperty("id", expect.any(String));
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
  expect(await pollSnapshot(request, base)).toBeNull();
});
