import type { Snapshot } from "../protocol";
import type { Action } from "./types";
import { PlayerCounters } from "./PlayerCounters";

export function GameSidebar({
  view,
  canPlay,
  disabled,
  act,
}: {
  view: Snapshot;
  canPlay: boolean;
  disabled: boolean;
  act: Action;
}) {
  const me = view.players[view.you];
  const usesCommanders = view.format !== "STANDARD";
  return (
    <aside className="sidebar">
      <section className="panel game-tools">
        <p className="eyebrow">TABLE TOOLS</p>
        {me && (
          <PlayerCounters
            usesCommanders={usesCommanders}
            player={me}
            players={view.players}
            act={act}
            disabled={!canPlay}
          />
        )}
        <details>
          <summary>Share private zones</summary>
          <p className="hint">
            Allow a player to inspect and manipulate your hand and search your
            library.
          </p>
          {view.players
            .filter((p) => p.seat !== view.you)
            .map((p) => (
              <label className="check" key={p.seat}>
                <input
                  type="checkbox"
                  disabled={!canPlay}
                  checked={view.grants.includes(p.seat)}
                  onChange={(e) =>
                    act("GRANT", {
                      target: p.seat,
                      allow: e.target.checked,
                    })
                  }
                />
                {p.name}
              </label>
            ))}
        </details>
        <button disabled={!canPlay} onClick={() => act("DICE", { sides: 6 })}>
          Roll a d6
        </button>
        <button
          className="quiet danger"
          disabled={!canPlay}
          onClick={() => {
            if (confirm("Concede this game? Your seat remains reserved."))
              act("CONCEDE");
          }}
        >
          Concede
        </button>
        {view.you === view.host && (
          <button
            className="quiet"
            disabled={disabled}
            onClick={() => {
              if (confirm("Mark this game finished?")) act("FINISH");
            }}
          >
            Finish game
          </button>
        )}
      </section>
      <ActionLog revision={view.revision} log={view.log} />
    </aside>
  );
}

function ActionLog({ revision, log }: { revision: number; log: string[] }) {
  return (
    <section className="panel action-log">
      <div className="zone-heading">
        <h3>Table log</h3>
        <span className="subtle">Live</span>
      </div>
      <ol>
        {[...log].reverse().map((line, i) => (
          <li key={`${revision}-${i}`}>{line}</li>
        ))}
      </ol>
    </section>
  );
}
