import type { Snapshot } from "../protocol";
import type { Action, OpenZone } from "./types";

export function YourResources({
  view,
  canPlay,
  act,
  openZone,
}: {
  view: Snapshot;
  canPlay: boolean;
  act: Action;
  openZone: OpenZone;
}) {
  const me = view.players[view.you];
  const usesCommanders = view.format !== "STANDARD";
  return (
    <>
      {me && (
        <div className="your-resources">
          <div className={`self-life seat-${view.you}`}>
            <span className="eyebrow">{me.name} · YOU</span>
            <div>
              <button
                aria-label="Lose 1 life"
                disabled={!canPlay}
                onClick={() => act("COUNTER", { name: "life", delta: -1 })}
              >
                −
              </button>
              <strong data-testid="your-life">{me.counters.life}</strong>
              <button
                aria-label="Gain 1 life"
                disabled={!canPlay}
                onClick={() => act("COUNTER", { name: "life", delta: 1 })}
              >
                +
              </button>
            </div>
          </div>
          <div className="zone-shortcuts">
            {[
              "library",
              "graveyard",
              "exile",
              ...(usesCommanders ? ["command"] : []),
            ].map((zone) => (
              <button
                key={zone}
                disabled={zone === "library" && !canPlay}
                onClick={() => openZone(view.you, zone)}
              >
                <span>{zone}</span>
                <strong>{me.counts[zone] || 0}</strong>
              </button>
            ))}
          </div>
          <div className="draw-controls">
            <button
              className="primary"
              disabled={!canPlay}
              onClick={() => act("DRAW", { count: 1 })}
            >
              Draw a card
            </button>
            <button disabled={!canPlay} onClick={() => act("SHUFFLE")}>
              Shuffle library
            </button>
          </div>
        </div>
      )}
    </>
  );
}
