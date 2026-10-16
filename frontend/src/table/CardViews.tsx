import type { Card, Player, Snapshot } from "../protocol";
import { groupBattlefield, type BattlefieldGroup } from "../battlefield";
import type { Action } from "./types";

export function PlayerArea({
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
export function CardButton({
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
export function Battlefield({
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
