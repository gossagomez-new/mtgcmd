import type { Action } from "../types";

export function CardCounterEditor({
  counter,
  setCounter,
  send,
}: {
  counter: string;
  setCounter: (name: string) => void;
  send: Action;
}) {
  return (
    <>
      <label>
        Card counter
        <input
          value={counter}
          onChange={(e) => setCounter(e.target.value)}
          maxLength={100}
        />
      </label>
      <div className="action-grid">
        <button
          disabled={!counter.trim()}
          onClick={() => send("CARD_COUNTER", { name: counter, delta: 1 })}
        >
          + Counter
        </button>
        <button
          disabled={!counter.trim()}
          onClick={() => send("CARD_COUNTER", { name: counter, delta: -1 })}
        >
          − Counter
        </button>
      </div>
    </>
  );
}
