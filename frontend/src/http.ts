import {
  decodeDraft,
  decodeSavedRooms,
  decodeSession,
  decodeSnapshot,
  ProtocolError,
  type GameFormat,
  type Snapshot,
} from "./protocol";
export class HttpError extends Error {
  override name = "HttpError";
  constructor(
    public readonly status: number,
    message: string,
    options?: ErrorOptions,
  ) {
    super(message, options);
  }
}
async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch (error) {
    if (error instanceof SyntaxError)
      throw new ProtocolError(
        "Could not read a server response. Refresh before retrying.",
      );
    throw error;
  }
}

async function httpFailure(
  response: Response,
  fallback: string,
): Promise<HttpError> {
  try {
    const value: unknown = await readJson(response);
    if (
      typeof value === "object" &&
      value !== null &&
      "message" in value &&
      typeof value.message === "string" &&
      value.message
    ) {
      return new HttpError(response.status, value.message);
    }
  } catch (cause) {
    return new HttpError(response.status, fallback, { cause });
  }
  return new HttpError(response.status, fallback);
}

async function decoded<T>(
  response: Response,
  decode: (value: unknown) => T,
  fallback = "Request failed",
): Promise<T> {
  if (!response.ok) throw await httpFailure(response, fallback);
  return decode(await readJson(response));
}

export async function getSession() {
  const response = await fetch("/api/session");
  if (!response.ok)
    throw new HttpError(
      response.status,
      "Could not connect to the table server.",
    );
  return decodeSession(await readJson(response));
}
export async function getSavedRooms() {
  const response = await fetch("/api/rooms");
  return response.ok ? decodeSavedRooms(await readJson(response)) : null;
}
export async function getRoom(id: string): Promise<Snapshot> {
  return decoded(
    await fetch(`/api/rooms/${encodeURIComponent(id)}`),
    decodeSnapshot,
    "Room unavailable",
  );
}
export function createTableApi(
  csrf: string,
  room: string,
  receive: (snapshot: Snapshot) => void,
) {
  async function post<T>(
    path: string,
    body: unknown,
    decode: (value: unknown) => T,
  ): Promise<T> {
    try {
      const response = await fetch(`/api${path}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf },
        body: JSON.stringify(body),
      });
      return await decoded(response, decode);
    } catch (error) {
      if (error instanceof HttpError && error.status === 409 && room) {
        try {
          receive(await getRoom(room));
        } catch (cause) {
          error.cause = cause;
        }
      }
      throw error;
    }
  }
  const path = `/rooms/${encodeURIComponent(room)}`;
  return {
    createRoom: (name: string, format: GameFormat) =>
      post("/rooms", { name, format }, decodeSnapshot),
    joinRoom: (name: string) => post(`${path}/join`, { name }, decodeSnapshot),
    previewDeck: (text: string, url: string) =>
      post(`${path}/decks/preview`, { text, url }, decodeDraft),
    loadDeck: (draftId: string, commanders: string[], revision: number) =>
      post(
        `${path}/deck`,
        {
          draftId,
          commanders,
          revision,
          commandId: crypto.randomUUID(),
        },
        decodeSnapshot,
      ),
  };
}
