import { useCardDrop } from "./useCardDrop";
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
              <ZoneShortcut
                key={zone}
                zone={zone}
                count={me.counts[zone] || 0}
                view={view}
                canPlay={canPlay}
                act={act}
                openZone={openZone}
              />
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

function ZoneShortcut({
  zone,
  count,
  view,
  canPlay,
  act,
  openZone,
}: {
  zone: string;
  count: number;
  view: Snapshot;
  canPlay: boolean;
  act: Action;
  openZone: OpenZone;
}) {
  const drop = useCardDrop(view.cards, zone, !canPlay, act);
  const destination =
    zone === "library" ? "bottom of the owner’s library" : `owner’s ${zone}`;
  return (
    <button
      {...drop}
      disabled={zone === "library" && !canPlay}
      title={`Drop to ${destination}`}
      onClick={() => openZone(view.you, zone)}
    >
      <span>{zone}</span>
      <strong>{count}</strong>
    </button>
  );
}
