import type { Snapshot, Draft } from "../protocol";
import type { Action } from "./types";
import { DeckImport } from "./DeckImport";
import { Readiness } from "./Readiness";
import { MulliganSettings } from "./MulliganSettings";

export function LobbyScreen({
  view,
  disabled,
  firstPlayer,
  setFirstPlayer,
  act,
  preview,
  loadDraft,
  fail,
}: {
  view: Snapshot;
  disabled: boolean;
  firstPlayer: number;
  setFirstPlayer: (seat: number) => void;
  act: Action;
  preview: (text: string, url: string) => Promise<Draft>;
  loadDraft: (draft: Draft, commanders: string[]) => Promise<void>;
  fail: (message: string) => void;
}) {
  const me = view.players[view.you];
  const usesCommanders = view.format !== "STANDARD";
  return (
    <main className="lobby">
      <div className="section-heading">
        <div>
          <p className="eyebrow">BEFORE THE FIRST DRAW</p>
          <h1>
            {usesCommanders
              ? "Gather your pod."
              : "Prepare your Standard duel."}
          </h1>
        </div>
        <span>
          {view.players.length} / {view.seatCapacity} seats filled
        </span>
      </div>
      <div className="lobby-layout">
        <section className="panel pod">
          <h2>The table</h2>
          <SeatList view={view} />
          <p className="hint">
            Use your usual voice app. The table handles cards and bookkeeping;
            you handle the rules.
          </p>
          <StartControls
            view={view}
            disabled={disabled}
            firstPlayer={firstPlayer}
            setFirstPlayer={setFirstPlayer}
            act={act}
          />
          <MulliganSettings view={view} disabled={disabled} act={act} />
        </section>
        <section className="panel deck-panel">
          <DeckImport
            format={view.format}
            disabled={disabled}
            preview={preview}
            load={loadDraft}
            fail={fail}
          />
          {me && (
            <Readiness
              key={me.deckRevision}
              player={me}
              disabled={disabled}
              act={act}
            />
          )}
        </section>
      </div>
    </main>
  );
}

function SeatList({ view }: { view: Snapshot }) {
  return (
    <>
      {Array.from({ length: view.seatCapacity }, (_, i) => {
        const p = view.players[i];
        return (
          <div className="seat-row" key={i}>
            <div className={`seat-icon seat-${i}`}>{i + 1}</div>
            <div>
              <strong>{p ? p.name : "Seat open"}</strong>
              <small>
                {p
                  ? p.ready
                    ? "Ready to play"
                    : p.deckCount
                      ? `${p.deckCount} cards loaded`
                      : "Choosing a deck"
                  : "Share the invite link"}
              </small>
            </div>
            {p && (
              <span className="seat-state">
                {p.ready ? "✓ READY" : "PREPARING"}
              </span>
            )}
          </div>
        );
      })}
    </>
  );
}

function StartControls({
  view,
  disabled,
  firstPlayer,
  setFirstPlayer,
  act,
}: {
  view: Snapshot;
  disabled: boolean;
  firstPlayer: number;
  setFirstPlayer: (seat: number) => void;
  act: Action;
}) {
  const usesCommanders = view.format !== "STANDARD";
  return (
    <>
      {view.you === view.host && (
        <>
          {!usesCommanders && (
            <label>
              Starting player
              <select
                value={firstPlayer}
                disabled={disabled}
                onChange={(event) => setFirstPlayer(Number(event.target.value))}
              >
                {view.players.map((player) => (
                  <option key={player.seat} value={player.seat}>
                    {player.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <button
            className="primary wide"
            disabled={
              disabled ||
              view.players.length !== view.seatCapacity ||
              !view.players.every((player) => player.ready)
            }
            onClick={() => act("START", usesCommanders ? {} : { firstPlayer })}
          >
            Start game
          </button>
        </>
      )}
    </>
  );
}
