import { useCallback, useEffect, useRef, useState } from "react";
import { commandPayload, type Message, type Snapshot } from "./protocol";

export function useGameSocket(
  room: string | undefined,
  receive: (view: Snapshot) => void,
  fail: (message: string) => void,
) {
  const socket = useRef<WebSocket | null>(null);
  const [connected, setConnected] = useState(false);
  const [online, setOnline] = useState<number[]>([]);
  const [pending, setPending] = useState(false);
  const deadline = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => {
    if (!room) return;
    let stopped = false;
    let attempt = 0;
    let retry: ReturnType<typeof setTimeout>;
    const connect = () => {
      const ws = new WebSocket(
        `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws?room=${encodeURIComponent(room)}`,
      );
      socket.current = ws;
      ws.onopen = () => {
        if (!stopped) {
          setConnected(true);
          attempt = 0;
        }
      };
      ws.onmessage = (event) => {
        if (stopped) return;
        try {
          const message: Message = JSON.parse(event.data);
          if (message.view) receive(message.view);
          if (message.online) setOnline(message.online);
          if (message.type === "ack" || message.type === "error") {
            clearTimeout(deadline.current);
            setPending(false);
          }
          if (message.type === "error")
            fail(message.message || "Action failed");
        } catch {
          fail("Could not read a server update. Refresh the table.");
        }
      };
      ws.onclose = () => {
        if (stopped) return;
        setConnected(false);
        setOnline([]);
        setPending(false);
        clearTimeout(deadline.current);
        retry = setTimeout(connect, Math.min(1000 * 2 ** attempt++, 10000));
      };
      ws.onerror = () => ws.close();
    };
    connect();
    return () => {
      stopped = true;
      clearTimeout(retry);
      clearTimeout(deadline.current);
      socket.current?.close();
      setConnected(false);
      setPending(false);
    };
  }, [room, receive, fail]);
  const send = useCallback(
    (revision: number, type: string, data: Record<string, unknown> = {}) => {
      if (socket.current?.readyState !== WebSocket.OPEN || pending) return;
      setPending(true);
      socket.current.send(JSON.stringify(commandPayload(revision, type, data)));
      deadline.current = setTimeout(() => {
        socket.current?.close();
        fail("No acknowledgment received. Reconnecting to check saved state.");
      }, 15000);
    },
    [pending, fail],
  );
  return { connected, online, pending, send };
}
