import { useEffect, useRef, useState } from "react";
import type { Snapshot } from "../protocol";
import { turnNotice, type NoticeStorage } from "../turnNotification";

export function TurnNotification({ view }: { view: Snapshot }) {
  const memory = useRef(new Map<string, string>());
  const [notice, setNotice] = useState({ key: "", text: "" });
  const { id, you, turn, turnVisit, active, status } = view;
  const currentKey = `${id}:${you}:${turnVisit === undefined ? "turn" : "visit"}:${turnVisit ?? turn}`;
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
    const text = turnNotice(
      { id, you, turn, turnVisit, active, status },
      storage,
    );
    if (text) setNotice({ key: currentKey, text });
  }, [id, you, turn, turnVisit, active, status, currentKey]);
  const visible =
    status === "playing" && active === you && notice.key === currentKey;
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
