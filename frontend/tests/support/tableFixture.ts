import {
  test as base,
  expect,
  type Page,
  type Route,
  type WebSocketRoute,
} from "@playwright/test";
import { z } from "zod";
import { decodeSnapshot, type Snapshot } from "../../src/protocol";
export { expect };
export const roomId = "00000000-0000-4000-8000-000000000001";
export type JsonReply = { status?: number; body: unknown };
const commandSchema = z.object({
  id: z.string().uuid(),
  revision: z.number().int().nonnegative(),
  type: z.string(),
  data: z.record(z.string(), z.unknown()),
});
export type CapturedCommand = z.infer<typeof commandSchema>;
type Observe = () => Promise<void>;
type Handler = (route: Route) => Promise<void>;

class DeferredReply {
  readonly promise: Promise<JsonReply>;
  private fulfill!: (reply: JsonReply) => void;
  private resolved = false;
  constructor(private readonly finished: () => void) {
    this.promise = new Promise((resolve) => {
      this.fulfill = resolve;
    });
  }
  resolve = (reply: JsonReply) => {
    if (this.resolved) return;
    this.resolved = true;
    this.finished();
    this.fulfill(reply);
  };
}

export class TableFixture {
  readonly commands: CapturedCommand[] = [];
  onCommand?: (command: CapturedCommand) => void | Promise<void>;
  private current?: Snapshot;
  private installed = false;
  private disposed = false;
  private cleanup?: Promise<void>;
  private socket?: WebSocketRoute;
  private readonly sockets = new Set<WebSocketRoute>();
  private readonly routes: { pattern: string; handler: Handler }[] = [];
  private readonly deferred = new Set<DeferredReply>();
  private readonly jobs = new Set<Promise<void>>();
  private readonly errors: unknown[] = [];
  private sessions = 0;
  private connections = 0;

  constructor(readonly page: Page) {}
  get view(): Snapshot {
    if (!this.current) throw new Error("No fixture snapshot installed");
    return this.current;
  }
  get pendingResponses(): number {
    return this.deferred.size;
  }
  counts = () => ({ sessions: this.sessions, connections: this.connections });

  private active() {
    if (this.disposed) throw new Error("Browser fixture is disposed");
  }
  private validate(view: Snapshot) {
    try {
      const result = decodeSnapshot(view);
      z.string().uuid().parse(result.id);
      return result;
    } catch (cause) {
      throw new Error("Invalid browser fixture snapshot or room UUID", {
        cause,
      });
    }
  }
  private async route(pattern: string, handler: Handler) {
    await this.page.route(pattern, handler);
    this.routes.push({ pattern, handler });
  }
  private track(work: () => Promise<void>) {
    const job = work()
      .catch((error) => {
        this.errors.push(error);
      })
      .finally(() => {
        this.jobs.delete(job);
      });
    this.jobs.add(job);
    return job;
  }

  install = async (view?: Snapshot) => {
    this.active();
    if (this.installed) throw new Error("Browser fixture is already installed");
    this.current = view ? this.validate(view) : undefined;
    this.installed = true;
    await this.route("**/api/session", async (route) => {
      this.sessions++;
      await route.fulfill({ json: { csrf: "token" } });
    });
    await this.route("**/api/rooms", (route) => route.fulfill({ json: [] }));
    if (this.current)
      await this.route(`**/api/rooms/${this.current.id}`, (route) =>
        route.fulfill({ json: this.view }),
      );
    await this.page.routeWebSocket("**/ws?room=**", (route) => {
      if (this.disposed) {
        void route.close().catch(() => {});
        return;
      }
      this.connections++;
      this.socket = route;
      this.sockets.add(route);
      route.onMessage((message) => {
        if (this.disposed) return;
        void this.track(async () => {
          const command = commandSchema.parse(JSON.parse(message.toString()));
          this.commands.push(command);
          await this.onCommand?.(command);
        });
      });
    });
  };

  open = async (view: Snapshot) => {
    try {
      await this.install(view);
      await this.page.goto(`/room/${view.id}`);
      await expect(
        this.page.getByText("Connected", { exact: true }),
      ).toBeVisible();
    } catch (error) {
      await this.dispose().catch(() => {});
      throw error;
    }
  };
  sendRaw = (text: string) => {
    this.active();
    if (!this.socket) throw new Error("No fixture WebSocket connection");
    this.socket.send(text);
  };
  snapshot = async (view: Snapshot, observe: Observe) => {
    this.current = this.validate(view);
    this.sendRaw(JSON.stringify({ type: "snapshot", view: this.current }));
    await observe();
  };
  acknowledge = async (view: Snapshot, commandId: string, observe: Observe) => {
    this.current = this.validate(view);
    this.sendRaw(
      JSON.stringify({ type: "ack", commandId, view: this.current }),
    );
    await observe();
  };

  captureJson = async <T>(
    pattern: string,
    schema: z.ZodType<T>,
    fallback: JsonReply | (() => JsonReply),
    method: "POST" | "GET" = "POST",
  ) => {
    this.active();
    const requests: T[] = [];
    const replies: (JsonReply | DeferredReply)[] = [];
    const defer = () => {
      this.active();
      const pending = new DeferredReply(() => {
        this.deferred.delete(pending);
      });
      this.deferred.add(pending);
      replies.push(pending);
      return pending;
    };
    await this.route(pattern, (route) =>
      this.track(async () => {
        if (this.disposed) {
          await route.abort();
          return;
        }
        if (route.request().method() !== method) {
          await route.fallback();
          return;
        }
        try {
          const body: unknown = JSON.parse(
            route.request().postData() ?? "null",
          );
          requests.push(schema.parse(body));
          const queued = replies.shift();
          const reply =
            queued instanceof DeferredReply
              ? await queued.promise
              : (queued ??
                (typeof fallback === "function" ? fallback() : fallback));
          await route.fulfill({
            status: reply.status ?? 200,
            json: reply.body,
          });
        } catch (error) {
          await route.abort().catch(() => {});
          throw error;
        }
      }),
    );
    return { requests, replies, defer };
  };
  settled = async () => {
    while (this.jobs.size) await Promise.all([...this.jobs]);
  };
  private async release() {
    this.disposed = true;
    for (const pending of this.deferred)
      pending.resolve({
        status: 503,
        body: { message: "Browser fixture disposed" },
      });
    await this.settled();
    if (!this.page.isClosed()) {
      const cleanup = await Promise.allSettled([
        ...[...this.sockets].map((socket) => socket.close()),
        ...this.routes.map(({ pattern, handler }) =>
          this.page.unroute(pattern, handler),
        ),
      ]);
      for (const result of cleanup)
        if (result.status === "rejected") this.errors.push(result.reason);
    }
    this.socket = undefined;
    this.sockets.clear();
    this.routes.length = 0;
    if (this.errors.length)
      throw new AggregateError(
        this.errors,
        "Browser fixture handler or cleanup failed",
      );
  }
  dispose = () => {
    this.cleanup ??= this.release();
    return this.cleanup;
  };
}

export const test = base.extend<{ mockTable: TableFixture }>({
  mockTable: async ({ page }, use) => {
    const table = new TableFixture(page);
    try {
      await use(table);
    } finally {
      await table.dispose();
    }
  },
});
