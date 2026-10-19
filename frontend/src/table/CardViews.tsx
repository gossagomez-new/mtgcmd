import { useState } from "react";
import { CardActionMenu } from "./CardActionMenu";
import type { Card, Player, Snapshot } from "../protocol";
import { groupBattlefield, type BattlefieldGroup } from "../battlefield";
import type { Action } from "./types";
import { useCardDrop } from "./useCardDrop";
import { useCardGestures } from "./useCardGestures";

export function PlayerArea({
  player,
  view,
  online,
  inspect,
  openZone,
  act,
  disabled,
  focus,
}: {
  act: Action;
  disabled: boolean;
  focus?: () => void;
  player: Player;
  view: Snapshot;
  online: boolean;
  inspect: (c: Card) => void;
  openZone: (seat: number, zone: string) => void;
}) {
  return (
    <section
      className={`opponent panel ${player.conceded ? "conceded" : ""} ${view.status === "playing" && view.active === player.seat ? "active-player" : ""}`}
      data-testid={`opponent-${player.seat}`}
    >
      {view.status === "playing" && view.active === player.seat && (
        <span className="active-turn-label">Active turn</span>
      )}
      <div className="player-heading">
        <span className={`seat-icon seat-${player.seat}`}>
          {player.seat + 1}
        </span>
        <div>
          <div className="player-identity">
            <strong className="player-name">{player.name}</strong>
            <strong
              className="opponent-life"
              aria-label={`${player.name} life`}
            >
              {player.counters.life}
            </strong>
          </div>
          <small>
            {player.conceded
              ? "Conceded"
              : online
                ? "At the table"
                : "Disconnected"}
          </small>
        </div>
      </div>
      {focus && (
        <button onClick={focus} aria-label={`Focus ${player.name} battlefield`}>
          Focus battlefield
        </button>
      )}
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
          act={act}
          disabled={disabled}
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
  act,
  disabled,
}: {
  act: Action;
  disabled: boolean;
  card: Card;
  inspect: (c: Card) => void;
}) {
  const [menu, setMenu] = useState<HTMLElement | null>(null);
  const { cancelInspection, ...gestures } = useCardGestures(
    card,
    inspect,
    act,
    disabled,
  );
  const presentation = cardPresentation(card);
  return (
    <div className="card-interaction">
      <button
        className={`card ${card.tapped ? "tapped" : ""} ${card.faceDown ? "face-down" : ""}`}
        aria-label={`Inspect ${presentation.name}`}
        {...gestures}
        onContextMenu={(event) => {
          event.preventDefault();
          cancelInspection();
          setMenu(event.currentTarget);
        }}
      >
        <CardArtwork presentation={presentation} />
        <span className="card-caption">{presentation.name}</span>
        <CardBadges card={card} />
      </button>
      <button
        className="card-actions-trigger"
        aria-label={`Actions for ${presentation.name}`}
        aria-haspopup="menu"
        aria-expanded={!!menu}
        onClick={(event) => {
          cancelInspection();
          setMenu(event.currentTarget);
        }}
      >
        Actions
      </button>
      {menu && (
        <CardActionMenu
          anchor={menu}
          card={card}
          disabled={disabled}
          act={act}
          inspect={inspect}
          close={() => setMenu(null)}
        />
      )}
    </div>
  );
}

function cardPresentation(card: Card) {
  const { name, image, type, text, faces = [] } = card.definition ?? {};
  const face = faces[card.face];
  return {
    name: face?.name || name || "Face-down card",
    image: face?.image || image,
    type: type || "Hidden identity",
    text,
  };
}

function CardArtwork({
  presentation,
}: {
  presentation: ReturnType<typeof cardPresentation>;
}) {
  const { name, image, type, text } = presentation;
  return image ? (
    <img src={image} alt={name} loading="lazy" />
  ) : (
    <div className="card-fallback">
      <span className="card-symbol">◈</span>
      <strong>{name}</strong>
      <small>{type}</small>
      <p>{text}</p>
    </div>
  );
}

function CardBadges({ card }: { card: Card }) {
  return (
    <>
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
    </>
  );
}

export function Battlefield({
  cards,
  allCards,
  inspect,
  act,
  disabled,
  testId,
  selection,
}: {
  cards: Card[];
  allCards: Card[];
  inspect: (c: Card) => void;
  act: Action;
  disabled: boolean;
  testId: string;
  selection?: PermanentSelection;
}) {
  const drop = useCardDrop(allCards, "battlefield", disabled, act);
  return (
    <div
      {...drop}
      className={`battlefield ${drop.className}`}
      data-testid={testId}
    >
      <BattlefieldGroups
        cards={cards}
        inspect={inspect}
        act={act}
        disabled={disabled}
        selection={selection}
      />
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
type PermanentSelection = {
  ids: string[];
  toggle: (id: string) => void;
};

function BattlefieldGroups({
  cards,
  inspect,
  act,
  disabled,
  selection,
}: {
  selection?: PermanentSelection;
  act: Action;
  disabled: boolean;
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
            <PermanentGroup
              key={group.type}
              group={group}
              inspect={inspect}
              act={act}
              disabled={disabled}
              selection={selection}
            />
          ))}
        </div>
      )}
      {lands && (
        <PermanentGroup
          group={lands}
          selection={selection}
          inspect={inspect}
          act={act}
          disabled={disabled}
        />
      )}
    </div>
  );
}

function PermanentGroup({
  group,
  inspect,
  act,
  disabled,
  selection,
}: {
  selection?: PermanentSelection;
  act: Action;
  disabled: boolean;
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
            {selection && card.editable && (
              <label className="permanent-selection">
                <input
                  type="checkbox"
                  aria-label={`Select ${card.definition?.faces[card.face]?.name || card.definition?.name || "Face-down card"}`}
                  checked={selection.ids.includes(card.id)}
                  disabled={disabled}
                  onChange={() => selection.toggle(card.id)}
                />
                Select
              </label>
            )}
            <CardButton
              card={card}
              inspect={inspect}
              act={act}
              disabled={disabled}
            />
          </div>
        ))}
      </div>
    </section>
  );
}
