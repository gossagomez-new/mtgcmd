import {
  expect,
  type APIRequestContext,
  type APIResponse,
} from "@playwright/test";
import { z } from "zod";
import {
  decodeServerMessage,
  decodeSnapshot,
  decodeSession,
  type Snapshot,
  type GameFormat,
} from "../../src/protocol";

export class SnapshotObserver {
  view?: Snapshot;
  readonly frames: string[] = [];
  private invalid = false;

  receive(payload: string | Buffer): void {
    const raw = payload.toString();
    this.frames.push(raw);
    try {
      const message = decodeServerMessage(raw);
      if ("view" in message && message.view !== undefined)
        this.view = message.view;
    } catch {
      this.invalid = true;
    }
  }

  assertHealthy(): void {
    if (this.invalid) throw new Error("Invalid server frame");
  }
}

const importRequestSchema = z.object({ url: z.string(), text: z.string() });
export const parseImportRequest = (body: unknown) =>
  importRequestSchema.parse(body);

function checkStatus(response: APIResponse): void {
  if (!response.ok()) throw new Error(`HTTP ${response.status()}`);
}

async function snapshotResponse(response: APIResponse): Promise<Snapshot> {
  checkStatus(response);
  const body: unknown = await response.json();
  return decodeSnapshot(body);
}

export async function readSnapshot(
  request: APIRequestContext,
  url: string,
): Promise<Snapshot> {
  return snapshotResponse(await request.get(url));
}

export async function readSession(request: APIRequestContext, url: string) {
  const response = await request.get(url);
  checkStatus(response);
  const body: unknown = await response.json();
  return decodeSession(body);
}

export async function pollSnapshot(
  request: APIRequestContext,
  url: string,
): Promise<Snapshot | null> {
  let response: APIResponse;
  try {
    response = await request.get(url);
  } catch {
    return null;
  }
  if ([502, 503, 504].includes(response.status())) return null;
  return snapshotResponse(response);
}

export async function waitForFormat(
  request: APIRequestContext,
  url: string,
  format: GameFormat,
): Promise<void> {
  let failure: unknown;
  let failed = false;
  await expect
    .poll(
      async () => {
        try {
          return (await pollSnapshot(request, url))?.format === format;
        } catch (error) {
          // End Playwright polling on terminal errors instead of retrying malformed data.
          failed = true;
          failure = error;
          return true;
        }
      },
      { timeout: 30000 },
    )
    .toBe(true);
  if (failed) throw failure;
}

export async function sendCommand(
  request: APIRequestContext,
  url: string,
  csrf: string,
  type: string,
  data: Record<string, unknown>,
): Promise<Snapshot> {
  const snapshot = await readSnapshot(request, url);
  const response = await request.post(`${url}/commands`, {
    headers: { "X-CSRF-Token": csrf, Origin: new URL(url).origin },
    data: { id: crypto.randomUUID(), revision: snapshot.revision, type, data },
  });
  return snapshotResponse(response);
}
