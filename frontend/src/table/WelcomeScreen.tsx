import type { FormEvent } from "react";
import type { GameFormat, SavedRoom } from "../protocol";

export function WelcomeScreen({
  room,
  name,
  setName,
  tableFormat,
  setTableFormat,
  csrf,
  busy,
  saved,
  enter,
}: {
  room: string;
  name: string;
  setName: (name: string) => void;
  tableFormat: GameFormat;
  setTableFormat: (format: GameFormat) => void;
  csrf: string;
  busy: boolean;
  saved: SavedRoom[];
  enter: (event: FormEvent) => void;
}) {
  return (
    <main className="welcome">
      <WelcomeCopy />
      <section className="welcome-form panel">
        <div className="table-emblem">◈</div>
        <h2>{room ? "Take your seat" : "Set up your table"}</h2>
        <p>
          {room
            ? "Your friends saved you a place."
            : "Choose a four-player Commander pod or a two-player Standard duel."}
        </p>
        <form onSubmit={enter}>
          <label>
            Your name
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={40}
              required
              placeholder="Planeswalker name"
            />
          </label>
          {!room && (
            <label>
              Table format
              <select
                value={tableFormat}
                onChange={(event) =>
                  setTableFormat(event.target.value as GameFormat)
                }
              >
                <option value="COMMANDER">
                  Commander · 4 players · 40 life
                </option>
                <option value="STANDARD">Standard · 2 players · 20 life</option>
              </select>
            </label>
          )}
          <button className="primary wide" disabled={!csrf || busy}>
            {busy ? "Connecting…" : room ? "Join table" : "Create table"}
          </button>
        </form>
        <SavedTables saved={saved} />
        <small>
          Your seat stays in this browser. Keep its cookies to return.
        </small>
      </section>
    </main>
  );
}

function WelcomeCopy() {
  return (
    <div className="welcome-copy">
      <p className="eyebrow">YOUR NEXT GAME NIGHT</p>
      <h1>
        Your next game.
        <br />
        One shared table.
      </h1>
      <p>Bring your deck, invite your pod, and make room for a little chaos.</p>
      <div className="welcome-details">
        <span>01 / Bring a Commander or Standard deck</span>
        <span>02 / Share the table link</span>
        <span>03 / Play together on voice</span>
      </div>
    </div>
  );
}

function SavedTables({ saved }: { saved: SavedRoom[] }) {
  return (
    <>
      {" "}
      {saved.length > 0 && (
        <div className="saved">
          <h3>Your saved tables</h3>
          {saved.map((s) => (
            <a key={s.id} href={`/room/${s.id}`}>
              {s.id.slice(0, 8)}{" "}
              <span>
                {s.format === "STANDARD" ? "Standard" : "Commander"} ·{" "}
                {s.status} ↗
              </span>
            </a>
          ))}
        </div>
      )}
    </>
  );
}
