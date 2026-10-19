import { useEffect, useRef, useState } from "react";
import type { Snapshot } from "../protocol";
import { turnNotice, type NoticeStorage } from "../turnNotification";

export function TurnNotification({ view }: { view: Snapshot }) {
  const memory = useRef(new Map<string, string>());
  const [notice, setNotice] = useState({ key: "", text: "" });
  const { id, you, turn, active, status } = view;
  useEffect(() => {
    const fallback = memory.current;
    const storage: NoticeStorage = {
      getItem(key) {
        try {
          return sessionStorage.getItem(key) ?? fallback.get(key) ?? null;
        } catch {
          return fallback.get(key) ?? null;
        }
      },
      setItem(key, value) {
        fallback.set(key, value);
        try {
          sessionStorage.setItem(key, value);
        } catch {
          /* Session-local memory still deduplicates notices. */
        }
      },
    };
    const text = turnNotice({ id, you, turn, active, status }, storage);
    if (text) setNotice({ key: `${id}:${you}:${turn}`, text });
  }, [id, you, turn, active, status]);
  const visible =
    status === "playing" &&
    active === you &&
    notice.key === `${id}:${you}:${turn}`;
  return (
    <div
      role="status"
      aria-label="Turn notification"
      aria-live="polite"
      className="turn-notification"
    >
      {visible ? notice.text : ""}
    </div>
  );
}
