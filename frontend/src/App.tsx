import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  acceptSnapshot,
  type Card,
  type GameFormat,
  type Player,
  type Snapshot,
  type Draft,
  type SavedRoom,
} from "./protocol";
import {
  createTableApi,
  getRoom,
  getSavedRooms,
  getSession,
  HttpError,
} from "./http";
import { useGameSocket } from "./useGameSocket";
import { groupBattlefield, type BattlefieldGroup } from "./battlefield";

type Action = (type: string, data?: Record<string, unknown>) => void;
const zones = [
  "library",
  "hand",
  "battlefield",
  "graveyard",
  "exile",
  "command",
  "stack",
];
const currentRoom = () =>
  /^\/room\/([a-f0-9-]{36})$/.exec(location.pathname)?.[1] || "";

export default function App() {
  const [csrf, setCsrf] = useState("");
  const [room, setRoom] = useState(currentRoom);
  const [view, setView] = useState<Snapshot | null>(null);
  const [saved, setSaved] = useState<SavedRoom[]>([]);
  const [tableFormat, setTableFormat] = useState<GameFormat>("COMMANDER");
  const [firstPlayer, setFirstPlayer] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [name, setName] = useState(localStorage.getItem("table-name") || "");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [viewer, setViewer] = useState<{ seat: number; zone: string } | null>(
    null,
  );
  const receive = useCallback(
    (next: Snapshot) => setView((previous) => acceptSnapshot(previous, next)),
    [],
  );
  const fail = useCallback((message: string) => setError(message), []);
  const socket = useGameSocket(view?.id, receive, fail);
  const api = createTableApi(csrf, room, receive);

  useEffect(() => {
    getSession()
      .then(async (session) => {
        setCsrf(session.csrf);
        const rooms = await getSavedRooms();
        if (rooms !== null) setSaved(rooms);
      })
      .catch((error: unknown) => fail(errorMessage(error)));
    const pop = () => {
      setView(null);
      setRoom(currentRoom());
      setSelectedId(null);
      setViewer(null);
    };
    window.addEventListener("popstate", pop);
    return () => window.removeEventListener("popstate", pop);
  }, [fail]);
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

  async function enter(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const next = await (room
        ? api.joinRoom(name)
        : api.createRoom(name, tableFormat));
      localStorage.setItem("table-name", name);
      history.pushState({}, "", `/room/${next.id}`);
      receive(next);
      setFirstPlayer(next.host);
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
  const selected = view?.cards.find((c) => c.id === selectedId);
  const disabled =
    !socket.connected || socket.pending || busy || view?.status === "finished";
  const me = view?.players[view.you];
  const usesCommanders = view?.format !== "STANDARD";
  const canPlay = !disabled && !me?.conceded;
  const inspect = (card: Card) => setSelectedId(card.id);
  const openZone = (seat: number, zone: string) => {
    if (zone === "library") act("SEARCH", { target: seat });
    setViewer({ seat, zone });
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
  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/">
          ◈{" "}
          <span>
            COMMANDER<span className="brand-light"> TABLE</span>
          </span>
        </a>
        <div className="topbar-right">
          {view && (
            <>
              <span className="room-label">
                TABLE {room.slice(0, 6).toUpperCase()}
              </span>
              <button
                className="quiet"
                onClick={() =>
                  navigator.clipboard
                    .writeText(location.href)
                    .catch(() => fail("Copy the URL from your address bar."))
                }
              >
                Copy invite
              </button>
            </>
          )}
          <span className={`connection ${socket.connected ? "live" : ""}`}>
            {view
              ? socket.connected
                ? "Connected"
                : "Reconnecting…"
              : "PRIVATE • FOUR PLAYERS"}
          </span>
        </div>
      </header>
      {error && (
        <div role="alert" className="error-banner">
          {error}
          <button aria-label="Dismiss error" onClick={() => setError("")}>
            ×
          </button>
        </div>
      )}
      {!view ? (
        <main className="welcome">
          <div className="welcome-copy">
            <p className="eyebrow">YOUR NEXT GAME NIGHT</p>
            <h1>
              Your next game.
              <br />
              One shared table.
            </h1>
            <p>
              Bring your deck, invite your pod, and make room for a little
              chaos.
            </p>
            <div className="welcome-details">
              <span>01 / Bring a Commander or Standard deck</span>
              <span>02 / Share the table link</span>
              <span>03 / Play together on voice</span>
            </div>
          </div>
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
                    <option value="STANDARD">
                      Standard · 2 players · 20 life
                    </option>
                  </select>
                </label>
              )}
              <button className="primary wide" disabled={!csrf || busy}>
                {busy ? "Connecting…" : room ? "Join table" : "Create table"}
              </button>
            </form>
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
            <small>
              Your seat stays in this browser. Keep its cookies to return.
            </small>
          </section>
        </main>
      ) : view.status === "lobby" ? (
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
              <p className="hint">
                Use your usual voice app. The table handles cards and
                bookkeeping; you handle the rules.
              </p>
              {view.you === view.host && (
                <>
                  {!usesCommanders && (
                    <label>
                      Starting player
                      <select
                        value={firstPlayer}
                        disabled={disabled}
                        onChange={(event) =>
                          setFirstPlayer(Number(event.target.value))
                        }
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
                    onClick={() =>
                      act("START", usesCommanders ? {} : { firstPlayer })
                    }
                  >
                    Start game
                  </button>
                </>
              )}
            </section>
            <section className="panel deck-panel">
              <DeckImport
                format={view.format}
                disabled={disabled}
                preview={(text, url) => api.previewDeck(text, url)}
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
      ) : (
        <main className="game-layout">
          <div className="table-area">
            <div className="board-scroll">
              <div
                className={`opponents ${usesCommanders ? "" : "duel-opponents"}`}
              >
                {view.players
                  .filter((p) => p.seat !== view.you)
                  .map((p) => (
                    <PlayerArea
                      key={p.seat}
                      player={p}
                      view={view}
                      online={socket.online.includes(p.seat)}
                      inspect={inspect}
                      openZone={openZone}
                    />
                  ))}
              </div>
              <div className="turn-strip">
                <div>
                  <span className="eyebrow">TURN {view.turn}</span>
                  <strong>
                    {view.players[view.active].name}
                    <span className="subtle"> / {view.phase}</span>
                  </strong>
                </div>
                <button
                  disabled={
                    !canPlay ||
                    (view.you !== view.active && view.you !== view.host)
                  }
                  onClick={() => act("PHASE")}
                >
                  Next phase →
                </button>
                <span className="phase-reminder">
                  Players manage priority and triggers
                </span>
              </div>
              <section className="stack panel">
                <div className="zone-heading">
                  <h3>
                    The stack{" "}
                    <span>
                      {view.cards.filter((c) => c.zone === "stack").length +
                        view.abilities.length}
                    </span>
                  </h3>
                  <button
                    disabled={!canPlay}
                    onClick={() => {
                      const text = prompt("Ability or trigger text");
                      if (text) act("ABILITY", { text });
                    }}
                  >
                    + Ability / trigger
                  </button>
                </div>
                <div className="stack-items">
                  {[
                    ...view.cards
                      .filter((c) => c.zone === "stack")
                      .map((c) => ({
                        order: c.stackOrder,
                        key: c.id,
                        content: <CardButton card={c} inspect={inspect} />,
                      })),
                    ...view.abilities.map((a) => ({
                      order: a.stackOrder,
                      key: a.id,
                      content: (
                        <div className="ability">
                          <small>{view.players[a.controller].name}</small>
                          <p>{a.text}</p>
                          {a.controller === view.you && (
                            <button
                              disabled={!canPlay}
                              onClick={() =>
                                act("RESOLVE_ABILITY", { abilityId: a.id })
                              }
                            >
                              Resolve / remove
                            </button>
                          )}
                        </div>
                      ),
                    })),
                  ]
                    .sort((a, b) => b.order - a.order)
                    .map((item) => (
                      <div key={item.key}>{item.content}</div>
                    ))}
                  {view.cards.every((c) => c.zone !== "stack") &&
                    !view.abilities.length && (
                      <p className="empty-state">
                        Spells and abilities meet here. Resolve them together.
                      </p>
                    )}
                </div>
              </section>
              <section className="your-field panel">
                <div className="zone-heading">
                  <h3>
                    Your battlefield <span>{me?.counts.battlefield || 0}</span>
                  </h3>
                  <button disabled={!canPlay} onClick={() => act("UNTAP_ALL")}>
                    Untap all
                  </button>
                </div>
                <Battlefield
                  cards={view.cards.filter(
                    (c) =>
                      c.zone === "battlefield" && c.controller === view.you,
                  )}
                  inspect={inspect}
                  act={act}
                  disabled={!canPlay}
                  testId="your-battlefield"
                />
                <div className="field-footer">
                  <button
                    disabled={!canPlay}
                    onClick={() => {
                      const name = prompt("Token name", "Creature token");
                      if (!name) return;
                      const text =
                        prompt("Power/toughness and rules text", "1/1") || "";
                      act("TOKEN", { name, text, type: "Creature — Token" });
                    }}
                  >
                    + Create token
                  </button>
                  <span>
                    Drag cards onto the battlefield or use their action menu
                  </span>
                </div>
              </section>
            </div>
            {me && (
              <div className="your-resources">
                <div className={`self-life seat-${view.you}`}>
                  <span className="eyebrow">{me.name} · YOU</span>
                  <div>
                    <button
                      aria-label="Lose 1 life"
                      disabled={!canPlay}
                      onClick={() =>
                        act("COUNTER", { name: "life", delta: -1 })
                      }
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
            <section className="hand panel">
              <div className="zone-heading">
                <h3>
                  Your hand <span>{me?.counts.hand || 0}</span>
                </h3>
                <span className="private-label">
                  ◈ Only you can see these cards
                </span>
              </div>
              <div className="hand-cards" data-testid="hand">
                {view.cards
                  .filter((c) => c.zone === "hand" && c.zoneOwner === view.you)
                  .map((c) => (
                    <CardButton key={c.id} card={c} inspect={inspect} />
                  ))}
                {!me?.counts.hand && (
                  <p className="empty-state">Your hand is empty.</p>
                )}
              </div>
            </section>
            {view.cards.some(
              (c) =>
                ["hand", "library"].includes(c.zone) &&
                c.zoneOwner !== view.you,
            ) && (
              <section className="panel reveals">
                <h3>Revealed / shared cards</h3>
                <div className="hand-cards">
                  {view.cards
                    .filter(
                      (c) =>
                        ["hand", "library"].includes(c.zone) &&
                        c.zoneOwner !== view.you,
                    )
                    .map((c) => (
                      <CardButton key={c.id} card={c} inspect={inspect} />
                    ))}
                </div>
              </section>
            )}
          </div>
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
                  Allow a player to inspect and manipulate your hand and search
                  your library.
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
              <button
                disabled={!canPlay}
                onClick={() => act("DICE", { sides: 6 })}
              >
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
            <section className="panel action-log">
              <div className="zone-heading">
                <h3>Table log</h3>
                <span className="subtle">Live</span>
              </div>
              <ol>
                {[...view.log].reverse().map((line, i) => (
                  <li key={`${view.revision}-${i}`}>{line}</li>
                ))}
              </ol>
            </section>
          </aside>
        </main>
      )}
      {view?.status === "finished" && (
        <div className="finished-banner">
          Game finished. Your table has been saved.
        </div>
      )}
      {viewer && view && (
        <ZoneViewer
          key={`${view.id}-${viewer.seat}-${viewer.zone}`}
          cards={view.cards}
          seat={viewer.seat}
          zone={viewer.zone}
          playerName={view.players[viewer.seat].name}
          inspect={inspect}
          close={() => {
            if (viewer.zone === "library") act("CLOSE_SEARCH");
            setViewer(null);
          }}
        />
      )}
      {selected && view && (
        <Inspector
          card={selected}
          view={view}
          act={act}
          disabled={!canPlay}
          close={() => setSelectedId(null)}
        />
      )}
    </div>
  );
}

function ZoneViewer({
  cards,
  seat,
  zone,
  playerName,
  inspect,
  close,
}: {
  cards: Card[];
  seat: number;
  zone: string;
  playerName: string;
  inspect: (card: Card) => void;
  close: () => void;
}) {
  const [query, setQuery] = useState("");
  const zoneCards = cards.filter(
    (card) => card.zone === zone && card.zoneOwner === seat,
  );
  const normalizedQuery = query.trim().toLowerCase();
  const matches = zoneCards
    .map((card, index) => ({ card, position: index + 1 }))
    .filter(({ card }) => {
      if (zone !== "library" || !normalizedQuery) return true;
      const names = card.definition
        ? [
            card.definition.name,
            ...card.definition.faces.map((face) => face.name),
          ]
        : ["Face-down card"];
      return names.some((name) => name.toLowerCase().includes(normalizedQuery));
    });
  return (
    <div className="modal-shade">
      <section
        className="zone-modal panel"
        role="dialog"
        aria-label="Zone viewer"
      >
        <div className="zone-heading">
          <h2>
            {playerName} / {zone}
          </h2>
          <button aria-label="Close zone viewer" onClick={close}>
            ×
          </button>
        </div>
        {zone === "library" && (
          <>
            <p className="hint">
              Private search. Close this view before shuffling. Use card actions
              to reorder cards to the top or bottom.
            </p>
            <div className="library-search">
              <label>
                Search library cards
                <input
                  type="search"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Card name or either face…"
                />
              </label>
              <button onClick={() => setQuery("")} disabled={!query}>
                Clear search
              </button>
            </div>
            <p className="hint" role="status">
              Showing {matches.length} of {zoneCards.length} cards.
            </p>
          </>
        )}
        <div className="zone-grid">
          {matches.map(({ card, position }) => (
            <div key={card.id}>
              {zone === "library" && <small>#{position}</small>}
              <CardButton card={card} inspect={inspect} />
            </div>
          ))}
        </div>
        {zoneCards.length === 0 ? (
          <p className="empty-state">No visible cards in this zone.</p>
        ) : (
          matches.length === 0 && (
            <p className="empty-state">No cards match your search.</p>
          )
        )}
      </section>
    </div>
  );
}

function DeckImport({
  format,
  disabled,
  preview,
  load,
  fail,
}: {
  format: GameFormat;
  disabled: boolean;
  preview: (text: string, url: string) => Promise<Draft>;
  load: (draft: Draft, commanders: string[]) => Promise<void>;
  fail: (s: string) => void;
}) {
  const [text, setText] = useState("");
  const [url, setUrl] = useState("");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [commanders, setCommanders] = useState<string[]>([]);
  const [pending, setPending] = useState(false);
  const usesCommanders = format !== "STANDARD";
  return (
    <>
      <p className="eyebrow">YOUR DECK</p>
      <h2>
        {usesCommanders ? "Bring your commander." : "Bring your Standard deck."}
      </h2>
      <p className="hint">
        Paste a deck export or import a public Moxfield link.
      </p>
      {!usesCommanders && (
        <p className="hint">
          60–250 main-deck cards. No commanders; sideboards are excluded.
          Legality and copy-limit warnings require your review.
        </p>
      )}
      <label>
        Moxfield URL
        <input
          value={url}
          onChange={(e) => {
            setUrl(e.target.value);
            setDraft(null);
          }}
          placeholder="https://moxfield.com/decks/…"
        />
      </label>
      <div className="divider">or paste a decklist</div>
      <label>
        Decklist
        <textarea
          rows={8}
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setDraft(null);
          }}
          placeholder={
            usesCommanders
              ? "Commander\n1 Gisa and Geralf\n\nDeck\n1 Sol Ring\n…"
              : "Deck\n24 Island\n4 Card name\n…"
          }
        />
      </label>
      <button
        disabled={disabled || pending || (!text.trim() && !url.trim())}
        onClick={async () => {
          setPending(true);
          try {
            const result = await preview(text, url);
            setDraft(result);
            setCommanders(usesCommanders ? result.suggested : []);
          } catch (e) {
            if (url && errorMessage(e).toLowerCase().includes("paste"))
              setUrl("");
            fail(errorMessage(e));
          } finally {
            setPending(false);
          }
        }}
      >
        {pending ? "Resolving cards…" : "Preview deck"}
      </button>
      {draft && (
        <div className="deck-preview">
          <h3>{draft.count} cards found</h3>
          {usesCommanders && (
            <>
              <label>
                Commander
                <select
                  value={commanders[0] || ""}
                  onChange={(e) =>
                    setCommanders([
                      e.target.value,
                      ...(commanders[1] ? [commanders[1]] : []),
                    ])
                  }
                >
                  <option value="">Choose commander</option>
                  {draft.choices.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Second commander (optional)
                <select
                  value={commanders[1] || ""}
                  onChange={(e) =>
                    setCommanders(
                      [commanders[0] || "", e.target.value].filter(Boolean),
                    )
                  }
                >
                  <option value="">None</option>
                  {draft.choices
                    .filter((c) => c.id !== commanders[0])
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                </select>
              </label>
            </>
          )}
          <button
            className="primary"
            disabled={
              disabled ||
              (usesCommanders && (!commanders.length || !commanders[0]))
            }
            onClick={() => void load(draft, usesCommanders ? commanders : [])}
          >
            Use this deck
          </button>
        </div>
      )}
    </>
  );
}
function Readiness({
  player,
  disabled,
  act,
}: {
  player: Player;
  disabled: boolean;
  act: Action;
}) {
  const [accepted, setAccepted] = useState(false);
  if (!player.deckCount) return null;
  return (
    <div className="readiness">
      <h3>{player.deckCount} cards loaded</h3>
      {player.warnings.length > 0 && (
        <>
          <ul className="warnings">
            {player.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
          <label className="check">
            <input
              type="checkbox"
              checked={accepted}
              onChange={(e) => setAccepted(e.target.checked)}
            />
            Accept deck warnings
          </label>
        </>
      )}
      {player.ready ? (
        <>
          <p className="ready-message">You are ready</p>
          <button
            disabled={disabled}
            onClick={() => act("READY", { ready: false })}
          >
            Change readiness
          </button>
        </>
      ) : (
        <button
          className="primary"
          disabled={disabled || (!!player.warnings.length && !accepted)}
          onClick={() =>
            act("READY", { ready: true, acceptWarnings: accepted })
          }
        >
          Ready to play
        </button>
      )}
    </div>
  );
}
function PlayerArea({
  player,
  view,
  online,
  inspect,
  openZone,
}: {
  player: Player;
  view: Snapshot;
  online: boolean;
  inspect: (c: Card) => void;
  openZone: (seat: number, zone: string) => void;
}) {
  return (
    <section
      className={`opponent panel ${player.conceded ? "conceded" : ""}`}
      data-testid={`opponent-${player.seat}`}
    >
      <div className="player-heading">
        <span className={`seat-icon seat-${player.seat}`}>
          {player.seat + 1}
        </span>
        <div>
          <strong>{player.name}</strong>
          <small>
            {player.conceded
              ? "Conceded"
              : online
                ? "At the table"
                : "Disconnected"}
          </small>
        </div>
        <strong className="opponent-life">{player.counters.life}</strong>
      </div>
      <div className="opponent-zones">
        <button
          disabled={!player.privateAccess}
          onClick={() => openZone(player.seat, "hand")}
        >
          Hand {player.counts.hand || 0}
        </button>
        <button
          disabled={!player.privateAccess}
          onClick={() => openZone(player.seat, "library")}
        >
          Library {player.counts.library || 0}
        </button>
        {[
          "graveyard",
          "exile",
          ...(view.format === "STANDARD" ? [] : ["command"]),
        ].map((z) => (
          <button key={z} onClick={() => openZone(player.seat, z)}>
            {z} {player.counts[z] || 0}
          </button>
        ))}
      </div>
      <div className="opponent-field">
        <BattlefieldGroups
          cards={view.cards.filter(
            (card) =>
              card.zone === "battlefield" && card.controller === player.seat,
          )}
          inspect={inspect}
        />
        {!player.counts.battlefield && (
          <span className="empty-state">Battlefield</span>
        )}
      </div>
      <div className="opponent-counters">
        {Object.entries(player.counters)
          .filter(
            ([n]) =>
              n !== "life" &&
              !n.startsWith("casts:") &&
              !n.startsWith("damage:"),
          )
          .map(([n, v]) => (
            <span key={n}>
              {n} {v}
            </span>
          ))}
        {view.players
          .flatMap((p) => p.commanders)
          .filter((c) => player.counters[`damage:${c.key}`])
          .map((c) => (
            <span key={c.key}>
              {c.name}: {player.counters[`damage:${c.key}`]} damage
            </span>
          ))}
      </div>
    </section>
  );
}
function CardButton({
  card,
  inspect,
}: {
  card: Card;
  inspect: (c: Card) => void;
}) {
  const face = card.definition?.faces[card.face];
  const name = face?.name || card.definition?.name || "Face-down card";
  const image = face?.image || card.definition?.image;
  return (
    <button
      className={`card ${card.tapped ? "tapped" : ""} ${card.faceDown ? "face-down" : ""}`}
      aria-label={`Inspect ${name}`}
      onClick={() => inspect(card)}
      draggable={card.editable && card.zone !== "battlefield"}
      onDragStart={(e) => {
        e.dataTransfer.setData("application/card", card.id);
        e.dataTransfer.effectAllowed = "move";
      }}
    >
      {image ? (
        <img src={image} alt={name} loading="lazy" />
      ) : (
        <div className="card-fallback">
          <span className="card-symbol">◈</span>
          <strong>{name}</strong>
          <small>{card.definition?.type || "Hidden identity"}</small>
          <p>{card.definition?.text}</p>
        </div>
      )}
      <span className="card-caption">{name}</span>
      {card.token && <span className="card-tag">TOKEN</span>}
      {card.faceDown && <span className="face-down-tag">FACE DOWN</span>}
      {card.combat && (
        <span className="combat-tag">
          {card.combat.startsWith("blocks:") ? "Blocking" : "Attacking"}
        </span>
      )}
      {Object.keys(card.counters).length > 0 && (
        <span className="counter-tag">
          {Object.entries(card.counters)
            .map(([n, v]) => `${n}: ${v}`)
            .join(" · ")}
        </span>
      )}
      {card.attachedTo && <span className="attachment-tag">Attached ↗</span>}
    </button>
  );
}
function Battlefield({
  cards,
  inspect,
  act,
  disabled,
  testId,
}: {
  cards: Card[];
  inspect: (c: Card) => void;
  act: Action;
  disabled: boolean;
  testId: string;
}) {
  return (
    <div
      className="battlefield"
      data-testid={testId}
      onDragOver={(e) => {
        if (!disabled) e.preventDefault();
      }}
      onDrop={(e) => {
        if (disabled) return;
        e.preventDefault();
        const cardId = e.dataTransfer.getData("application/card");
        if (!cardId) return;
        if (!cards.some((card) => card.id === cardId)) {
          act("MOVE", { cardId, zone: "battlefield" });
        }
      }}
    >
      <BattlefieldGroups cards={cards} inspect={inspect} />
      {!cards.length && (
        <div className="field-empty">
          <span>◈</span>
          <p>Your battlefield is waiting.</p>
          <small>Play a card from your hand to begin.</small>
        </div>
      )}
    </div>
  );
}
function BattlefieldGroups({
  cards,
  inspect,
}: {
  cards: Card[];
  inspect: (card: Card) => void;
}) {
  const groups = groupBattlefield(cards);
  if (!groups.length) return null;
  const nonlands = groups.filter((group) => group.type !== "land");
  const lands = groups.find((group) => group.type === "land");
  return (
    <div className="battlefield-layout">
      {nonlands.length > 0 && (
        <div className="battlefield-nonlands">
          {nonlands.map((group) => (
            <PermanentGroup key={group.type} group={group} inspect={inspect} />
          ))}
        </div>
      )}
      {lands && <PermanentGroup group={lands} inspect={inspect} />}
    </div>
  );
}

function PermanentGroup({
  group,
  inspect,
}: {
  group: BattlefieldGroup;
  inspect: (card: Card) => void;
}) {
  return (
    <section
      className={`battlefield-group ${group.type === "land" ? "battlefield-lands" : ""}`}
      role="group"
      aria-label={group.label}
    >
      <h4>{group.label}</h4>
      <div className="battlefield-group-cards">
        {group.cards.map((card) => (
          <div className="battlefield-card-slot" key={card.id}>
            <CardButton card={card} inspect={inspect} />
          </div>
        ))}
      </div>
    </section>
  );
}

function PlayerCounters({
  usesCommanders,
  player,
  players,
  act,
  disabled,
}: {
  usesCommanders: boolean;
  player: Player;
  players: Player[];
  act: Action;
  disabled: boolean;
}) {
  const [name, setName] = useState("");
  const rows = Object.entries(player.counters).filter(
    ([n]) =>
      n !== "life" && !n.startsWith("casts:") && !n.startsWith("damage:"),
  );
  return (
    <>
      <div className="counter-rows">
        {rows.map(([n, v]) => (
          <CounterRow
            key={n}
            label={n}
            value={v}
            disabled={disabled}
            change={(delta) => act("COUNTER", { name: n, delta })}
          />
        ))}
      </div>
      <form
        className="inline-form"
        onSubmit={(e) => {
          e.preventDefault();
          if (name.trim()) {
            act("COUNTER", { name: name.trim(), delta: 1 });
            setName("");
          }
        }}
      >
        <input
          aria-label="New player counter"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={100}
          placeholder="Energy, experience…"
        />
        <button disabled={disabled || !name.trim()}>+</button>
      </form>
      {usesCommanders && (
        <>
          <details>
            <summary>Commander damage received</summary>
            {players
              .flatMap((p) => p.commanders)
              .map((c) => (
                <CounterRow
                  key={c.key}
                  label={c.name}
                  value={player.counters[`damage:${c.key}`] || 0}
                  disabled={disabled}
                  change={(delta) =>
                    act("COUNTER", { name: `damage:${c.key}`, delta })
                  }
                />
              ))}
          </details>
          <details>
            <summary>Your commander casts</summary>
            {player.commanders.map((c) => (
              <div key={c.key}>
                <CounterRow
                  label={c.name}
                  value={c.casts}
                  disabled={disabled}
                  change={(delta) =>
                    act("COMMANDER_CAST", { key: c.key, delta })
                  }
                />
                <small>
                  Additional commander tax: {Math.max(0, c.casts) * 2}
                </small>
              </div>
            ))}
          </details>
        </>
      )}
    </>
  );
}
function CounterRow({
  label,
  value,
  change,
  disabled,
}: {
  label: string;
  value: number;
  change: (delta: number) => void;
  disabled: boolean;
}) {
  return (
    <div className="counter-row">
      <span>{label}</span>
      <button
        aria-label={`Decrease ${label}`}
        disabled={disabled}
        onClick={() => change(-1)}
      >
        −
      </button>
      <strong>{value}</strong>
      <button
        aria-label={`Increase ${label}`}
        disabled={disabled}
        onClick={() => change(1)}
      >
        +
      </button>
    </div>
  );
}
function Inspector({
  card,
  view,
  act,
  disabled,
  close,
}: {
  card: Card;
  view: Snapshot;
  act: Action;
  disabled: boolean;
  close: () => void;
}) {
  const [counter, setCounter] = useState("+1/+1");
  const [target, setTarget] = useState(view.you);
  const [attachment, setAttachment] = useState("");
  const [blocker, setBlocker] = useState("");
  const face = card.definition?.faces[card.face];
  const image = face?.image || card.definition?.image;
  const name = face?.name || card.definition?.name || "Face-down card";
  const editable = card.editable && !disabled;
  const action = (type: string, data: Record<string, unknown> = {}) =>
    act(type, { cardId: card.id, ...data });
  return (
    <aside
      className="inspector panel"
      role="dialog"
      aria-label="Card inspector"
    >
      <div className="zone-heading">
        <h2>{name}</h2>
        <button aria-label="Close inspector" onClick={close}>
          ×
        </button>
      </div>
      {image && <img className="inspect-image" src={image} alt={name} />}
      <p className="card-type">{face?.type || card.definition?.type}</p>
      <p className="rules-text">
        {face?.text ||
          card.definition?.text ||
          "This card’s identity is hidden."}
      </p>
      <small>
        Owner: {view.players[card.owner].name} · Controller:{" "}
        {view.players[card.controller].name}
      </small>
      <p className="eyebrow">
        {card.zone}
        {card.tapped ? " · TAPPED" : ""}
      </p>
      {editable && (
        <>
          <div className="action-grid">
            <button
              onClick={() => action("MOVE", { zone: "exile", faceDown: true })}
            >
              Exile face down
            </button>
            {card.zone !== "battlefield" && (
              <>
                <button
                  className="primary"
                  onClick={() => action("MOVE", { zone: "battlefield" })}
                >
                  Play to battlefield
                </button>
                <button
                  onClick={() =>
                    action("MOVE", { zone: "battlefield", faceDown: true })
                  }
                >
                  Play face down
                </button>
              </>
            )}
            {card.zone === "battlefield" && (
              <>
                <button onClick={() => action("TAP")}>
                  {card.tapped ? "Untap" : "Tap"}
                </button>
                <button onClick={() => action("FACE_DOWN")}>
                  {card.faceDown ? "Turn face up" : "Turn face down"}
                </button>
              </>
            )}
            <button
              onClick={() =>
                action("REVEAL", {
                  audience: view.players
                    .filter((p) => p.seat !== view.you)
                    .map((p) => p.seat),
                })
              }
            >
              Reveal to table
            </button>
            <button onClick={() => action("REVEAL", { audience: [] })}>
              End reveal
            </button>
            {(card.definition?.faces.length || 0) > 1 && (
              <button onClick={() => action("TRANSFORM")}>Other face</button>
            )}
          </div>
          <label>
            Move to zone
            <select
              value=""
              onChange={(e) => {
                if (e.target.value) action("MOVE", { zone: e.target.value });
              }}
            >
              <option value="">Choose destination</option>
              {zones
                .filter((z) => z !== "command" || card.commanderKey)
                .map((z) => (
                  <option key={z} value={z}>
                    {z}
                  </option>
                ))}
            </select>
          </label>
          <div className="action-grid">
            <button
              onClick={() => action("MOVE", { zone: "library", top: true })}
            >
              Library top
            </button>
            <button onClick={() => action("MOVE", { zone: "library" })}>
              Library bottom
            </button>
          </div>
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
              onClick={() =>
                action("CARD_COUNTER", { name: counter, delta: 1 })
              }
            >
              + Counter
            </button>
            <button
              disabled={!counter.trim()}
              onClick={() =>
                action("CARD_COUNTER", { name: counter, delta: -1 })
              }
            >
              − Counter
            </button>
          </div>
          {card.zone === "battlefield" && (
            <>
              <label>
                Transfer control
                <select
                  value={target}
                  onChange={(e) => setTarget(Number(e.target.value))}
                >
                  {view.players.map((p) => (
                    <option key={p.seat} value={p.seat}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </label>
              <button onClick={() => action("CONTROL", { target })}>
                Give control
              </button>
              <label>
                Attach to
                <select
                  value={attachment}
                  onChange={(e) => setAttachment(e.target.value)}
                >
                  <option value="">None / detach</option>
                  {view.cards
                    .filter((c) => c.zone === "battlefield" && c.id !== card.id)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.definition?.name || "Face-down card"} ·{" "}
                        {view.players[c.controller].name}
                      </option>
                    ))}
                </select>
              </label>
              <button
                onClick={() => action("ATTACH", { targetId: attachment })}
              >
                Set attachment
              </button>
              <div className="action-grid">
                <button
                  onClick={() => action("COMBAT", { marker: "attacker" })}
                >
                  Mark attacker
                </button>
                <button onClick={() => action("COMBAT", { marker: "" })}>
                  Clear combat
                </button>
              </div>
              <label>
                Block attacker
                <select
                  value={blocker}
                  onChange={(e) => setBlocker(e.target.value)}
                >
                  <option value="">Choose attacker</option>
                  {view.cards
                    .filter(
                      (c) =>
                        c.zone === "battlefield" &&
                        c.combat === "attacker" &&
                        c.id !== card.id,
                    )
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.definition?.name || "Face-down card"}
                      </option>
                    ))}
                </select>
              </label>
              <button
                disabled={!blocker}
                onClick={() =>
                  action("COMBAT", { marker: `blocks:${blocker}` })
                }
              >
                Mark blocker
              </button>
            </>
          )}
          <details>
            <summary>Reveal to one player</summary>
            <select
              aria-label="Reveal audience"
              value={target}
              onChange={(e) => setTarget(Number(e.target.value))}
            >
              {view.players.map((p) => (
                <option key={p.seat} value={p.seat}>
                  {p.name}
                </option>
              ))}
            </select>
            <button onClick={() => action("REVEAL", { audience: [target] })}>
              Reveal to selected player
            </button>
          </details>
          {card.token && (
            <button
              onClick={() => {
                const name = prompt("Token name", card.definition?.name);
                if (!name) return;
                const type = prompt("Token type", card.definition?.type) || "";
                const text =
                  prompt("Power/toughness and text", card.definition?.text) ||
                  "";
                action("EDIT_TOKEN", { name, type, text });
              }}
            >
              Edit token characteristics
            </button>
          )}
        </>
      )}
      {card.definition &&
        !disabled &&
        !["library", "hand"].includes(card.zone) && (
          <button onClick={() => action("COPY")}>Create token copy</button>
        )}
    </aside>
  );
}
function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Request failed";
}
