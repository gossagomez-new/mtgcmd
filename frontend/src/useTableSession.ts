import { useCallback, useEffect, useState } from "react";
import {
  acceptSnapshot,
  type Snapshot,
  type SavedRoom,
  type GameFormat,
  type Draft,
} from "./protocol";
import {
  createTableApi,
  getRoom,
  getSavedRooms,
  getSession,
  HttpError,
} from "./http";
import { useGameSocket } from "./useGameSocket";
import { errorMessage } from "./errors";
import type { Action } from "./table/types";
const currentRoom = () =>
  /^\/room\/([a-f0-9-]{36})$/.exec(location.pathname)?.[1] || "";

export function useTableSession(
  onNavigate: () => void,
  onEnter: (host: number) => void,
) {
  const [room, setRoom] = useState(currentRoom);
  const [view, setView] = useState<Snapshot | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const receive = useCallback(
    (next: Snapshot) => setView((previous) => acceptSnapshot(previous, next)),
    [],
  );
  const fail = useCallback((message: string) => setError(message), []);
  const { csrf, saved } = useGuestSession(fail);
  const socket = useGameSocket(view?.id, receive, fail);
  const api = createTableApi(csrf, room, receive);

  useEffect(() => {
    const pop = () => {
      setView(null);
      setRoom(currentRoom());
      onNavigate();
    };
    window.addEventListener("popstate", pop);
    return () => window.removeEventListener("popstate", pop);
  }, [fail, onNavigate]);
  useRoomSnapshot(csrf, room, receive, fail);

  async function enter(name: string, tableFormat: GameFormat) {
    setBusy(true);
    setError("");
    try {
      const next = await (room
        ? api.joinRoom(name)
        : api.createRoom(name, tableFormat));
      localStorage.setItem("table-name", name);
      history.pushState({}, "", `/room/${next.id}`);
      receive(next);
      onEnter(next.host);
      setRoom(next.id);
    } catch (e) {
      fail(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  const act: Action = (type, data = {}) => {
    if (view) {
      setError("");
      socket.send(view.revision, type, data);
    }
  };
  async function loadDraft(draft: Draft, commanders: string[]) {
    if (!view) return;
    setBusy(true);
    setError("");
    try {
      receive(await api.loadDeck(draft.draftId, commanders, view.revision));
    } catch (e) {
      fail(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return {
    csrf,
    room,
    view,
    saved,
    error,
    busy,
    socket,
    api,
    enter,
    act,
    loadDraft,
    fail,
  };
}

function useRoomSnapshot(
  csrf: string,
  room: string,
  receive: (snapshot: Snapshot) => void,
  fail: (message: string) => void,
) {
  useEffect(() => {
    if (!csrf || !room) return;
    let cancelled = false;
    getRoom(room)
      .then((next) => {
        if (!cancelled) receive(next);
      })
      .catch((error: unknown) => {
        if (!cancelled && !(error instanceof HttpError && error.status === 403))
          fail(errorMessage(error));
      });
    return () => {
      cancelled = true;
    };
  }, [csrf, room, receive, fail]);
}

function useGuestSession(fail: (message: string) => void) {
  const [csrf, setCsrf] = useState("");
  const [saved, setSaved] = useState<SavedRoom[]>([]);
  useEffect(() => {
    getSession()
      .then(async (session) => {
        setCsrf(session.csrf);
        const rooms = await getSavedRooms();
        if (rooms !== null) setSaved(rooms);
      })
      .catch((error: unknown) => fail(errorMessage(error)));
  }, [fail]);
  return { csrf, saved };
}
