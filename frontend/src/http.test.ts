import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createTableApi,
  getRoom,
  getSavedRooms,
  getSession,
  HttpError,
} from "./http";
import { ProtocolError } from "./protocol";
import { definitionFixture, snapshotFixture } from "./protocolFixtures";

afterEach(() => vi.unstubAllGlobals());
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status });

describe("HTTP decoding and recovery", () => {
  it("decodes GET endpoints and preserves ignored room-list failures", async () => {
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(json({ csrf: "token" }))
      .mockResolvedValueOnce(
        json([{ id: "room", status: "playing", format: "STANDARD" }]),
      )
      .mockResolvedValueOnce(json(snapshotFixture()))
      .mockResolvedValueOnce(new Response("unavailable", { status: 500 }));
    vi.stubGlobal("fetch", fetch);
    expect(await getSession()).toEqual({ csrf: "token" });
    expect(await getSavedRooms()).toHaveLength(1);
    expect(await getRoom("room")).toEqual(snapshotFixture());
    expect(await getSavedRooms()).toBeNull();
    expect(fetch.mock.calls.map(([path]) => path)).toEqual([
      "/api/session",
      "/api/rooms",
      "/api/rooms/room",
      "/api/rooms",
    ]);
  });

  it("uses endpoint decoders and keeps POST headers and payloads", async () => {
    const draft = {
      draftId: "draft",
      choices: [definitionFixture()],
      suggested: [],
      count: 60,
    };
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(json(snapshotFixture()))
      .mockResolvedValueOnce(json(snapshotFixture()))
      .mockResolvedValueOnce(json(draft))
      .mockResolvedValueOnce(json(snapshotFixture()));
    vi.stubGlobal("fetch", fetch);
    const api = createTableApi("csrf-token", "room", vi.fn());
    await api.createRoom("Alice", "STANDARD");
    await api.joinRoom("Alice");
    expect(await api.previewDeck("1 Card", "")).toEqual(draft);
    await api.loadDeck("draft", [], 7);
    expect(fetch).toHaveBeenCalledTimes(4);
    const options = fetch.mock.calls[3][1]!;
    expect(options.method).toBe("POST");
    expect(options.headers).toEqual({
      "Content-Type": "application/json",
      "X-CSRF-Token": "csrf-token",
    });
    if (typeof options.body !== "string")
      throw new Error("Expected a JSON request body");
    const body: unknown = JSON.parse(options.body);
    expect(body).toMatchObject({
      draftId: "draft",
      commanders: [],
      revision: 7,
    });
    if (typeof body !== "object" || body === null || !("commandId" in body))
      throw new Error("Missing command ID");
    expect(typeof body.commandId).toBe("string");
  });

  it.each([400, 403, 500])(
    "retains HTTP status %i and validated messages",
    async (status) => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue(json({ message: "Denied" }, status)),
      );
      await expect(getRoom("room")).rejects.toMatchObject({
        status,
        message: "Denied",
      });
    },
  );

  it.each([
    "<html>PRIVATE-CARD-SECRET</html>",
    "",
    '{"message":null}',
    '{"message":42}',
  ])("uses safe fallback for invalid error bodies %#", async (body) => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(body, { status: 403 })),
    );
    await expect(getRoom("room")).rejects.toMatchObject({
      status: 403,
      message: "Room unavailable",
    });
  });

  it("preserves the initial session failure message", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(json({ message: "Other" }, 500)),
    );
    await expect(getSession()).rejects.toThrow(
      "Could not connect to the table server.",
    );
  });

  it.each(["not JSON", "null", '{"id":"room"}'])(
    "rejects bad successful responses without retrying mutations %#",
    async (body) => {
      const fetch = vi
        .fn<typeof globalThis.fetch>()
        .mockResolvedValue(new Response(body));
      vi.stubGlobal("fetch", fetch);
      await expect(
        createTableApi("csrf", "room", vi.fn()).joinRoom("Alice"),
      ).rejects.toBeInstanceOf(ProtocolError);
      expect(fetch).toHaveBeenCalledTimes(1);
    },
  );

  it("refreshes once on conflict, applies the decoded view, and never retries POST", async () => {
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(json({ message: "Stale revision" }, 409))
      .mockResolvedValueOnce(json(snapshotFixture()));
    vi.stubGlobal("fetch", fetch);
    const receive = vi.fn();
    await expect(
      createTableApi("csrf", "room", receive).joinRoom("Alice"),
    ).rejects.toMatchObject({ status: 409, message: "Stale revision" });
    expect(receive).toHaveBeenCalledWith(snapshotFixture());
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(fetch.mock.calls[1][0]).toBe("/api/rooms/room");
  });

  it("keeps a conflict visible even when its body or recovery payload is invalid", async () => {
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(new Response("invalid", { status: 409 }))
      .mockResolvedValueOnce(json({ id: "room" }));
    vi.stubGlobal("fetch", fetch);
    const receive = vi.fn();
    const error = await createTableApi("csrf", "room", receive)
      .joinRoom("Alice")
      .catch((error: unknown) => error);
    expect(error).toBeInstanceOf(HttpError);
    if (!(error instanceof HttpError))
      throw new Error("Expected an HTTP error");
    expect(error.cause).toBeInstanceOf(ProtocolError);
    expect(error).toMatchObject({
      status: 409,
      message: "Request failed",
    });
    expect(receive).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("retains a failed recovery cause and skips recovery without a current room", async () => {
    const network = new TypeError("offline");
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(json({ message: "Stale" }, 409))
      .mockRejectedValueOnce(network)
      .mockResolvedValueOnce(json({ message: "Stale" }, 409));
    vi.stubGlobal("fetch", fetch);
    await expect(
      createTableApi("csrf", "room", vi.fn()).joinRoom("Alice"),
    ).rejects.toMatchObject({ status: 409, cause: network });
    await expect(
      createTableApi("csrf", "", vi.fn()).createRoom("Alice", "STANDARD"),
    ).rejects.toMatchObject({ status: 409 });
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it.each([
    new TypeError("offline"),
    new DOMException("cancelled", "AbortError"),
  ])("preserves network/abort failure identity %#", async (error) => {
    const fetch = vi.fn<typeof globalThis.fetch>().mockRejectedValue(error);
    vi.stubGlobal("fetch", fetch);
    await expect(getRoom("room")).rejects.toBe(error);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("preserves cancellation while reading an otherwise successful response body", async () => {
    const error = new DOMException("cancelled", "AbortError");
    const body = new ReadableStream({
      start(controller) {
        controller.error(error);
      },
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(body)));
    await expect(getRoom("room")).rejects.toBe(error);
  });
});
