import { useState } from "react";
import type { Card, Snapshot } from "../protocol";
import type { Action, Inspect } from "./types";
import { mulliganSettings } from "./MulliganSettings";
import { CardArtworkPreview } from "./CardArtworkPreview";

export function OpeningHands({
  view,
  disabled,
  act,
  inspect,
}: {
  view: Snapshot;
  disabled: boolean;
  act: Action;
  inspect: Inspect;
}) {
  const settings = mulliganSettings(view);
  const own = settings.players.find((player) => player.seat === view.you);
  const cards = view.cards.filter(
    (card) => card.zone === "hand" && card.zoneOwner === view.you,
  );
  return (
    <main className="opening-screen panel">
      <h1>Choose your opening hand</h1>
      <p>
        {settings.mode === "LONDON"
          ? `London · ${settings.freeMulligans} free mulligan(s). Redraw seven; bottom cards only when you keep.`
          : "Unlimited seven-card redraws. Keep without bottoming."}
      </p>
      <ul aria-label="Opening-hand progress">
        {settings.players.map((progress) => (
          <li key={progress.seat}>
            {view.players.find((player) => player.seat === progress.seat)?.name}
            : {progress.kept ? "Kept" : "Choosing"} · {progress.redraws}{" "}
            mulligan(s)
          </li>
        ))}
      </ul>
      {own && (
        <HandChoice
          key={cards.map((card) => card.id).join(",")}
          cards={cards}
          kept={own.kept}
          debt={own.bottomCount}
          redrawDisabled={
            settings.mode === "LONDON" &&
            own.redraws - settings.freeMulligans >= cards.length
          }
          disabled={disabled}
          act={act}
          inspect={inspect}
        />
      )}
      {view.you === view.host && (
        <button disabled={disabled} onClick={() => act("FINISH")}>
          Cancel game
        </button>
      )}
    </main>
  );
}

function HandChoice({
  cards,
  kept,
  debt,
  redrawDisabled,
  disabled,
  act,
  inspect,
}: {
  cards: Card[];
  kept: boolean;
  debt: number;
  redrawDisabled: boolean;
  disabled: boolean;
  act: Action;
  inspect: Inspect;
}) {
  const [bottom, setBottom] = useState<string[]>([]);
  const blocked = disabled || kept;
  const toggle = (id: string) =>
    setBottom((current) =>
      current.includes(id)
        ? current.filter((value) => value !== id)
        : current.length < debt
          ? [...current, id]
          : current,
    );
  const move = (index: number, offset: number) =>
    setBottom((current) => {
      const next = [...current];
      [next[index], next[index + offset]] = [next[index + offset], next[index]];
      return next;
    });
  return (
    <section aria-label="Your opening hand">
      {kept ? (
        <p role="status">Waiting for other players</p>
      ) : (
        <p role="status">
          Select {debt} card(s) to put on the bottom before keeping.
        </p>
      )}
      <div className="opening-cards" data-testid="hand">
        {cards.map((card) => (
          <OpeningCard
            key={card.id}
            card={card}
            inspect={inspect}
            kept={kept}
            selected={bottom.includes(card.id)}
            disabled={
              blocked ||
              debt === 0 ||
              (!bottom.includes(card.id) && bottom.length >= debt)
            }
            select={() => toggle(card.id)}
          />
        ))}
      </div>
      {cards.length === 0 && <p>Your hand is empty.</p>}
      {!kept && (
        <>
          <BottomOrder
            cards={cards}
            bottom={bottom}
            blocked={blocked}
            move={move}
            remove={toggle}
          />
          <div className="actions">
            <button
              disabled={blocked || redrawDisabled || cards.length === 0}
              onClick={() => act("MULLIGAN")}
            >
              Mulligan
            </button>
            <button
              className="primary"
              disabled={blocked || bottom.length !== debt}
              onClick={() => act("KEEP_HAND", { bottom })}
            >
              Keep hand
            </button>
          </div>
        </>
      )}
    </section>
  );
}

function OpeningCard({
  card,
  inspect,
  kept,
  selected,
  disabled,
  select,
}: {
  card: Card;
  inspect: Inspect;
  kept: boolean;
  selected: boolean;
  disabled: boolean;
  select: () => void;
}) {
  const definition = card.definition ?? {
    name: "Card",
    image: "",
    type: "",
    text: "",
    faces: [],
  };
  const name = definition.name;
  const face = definition.faces[card.face];
  const image = face?.image || definition.image;
  return (
    <article className="opening-card">
      <CardArtworkPreview image={image} name={name} blocked={false}>
        <button
          className="opening-art"
          aria-label={`Inspect ${name}`}
          onClick={() => inspect(card)}
        >
          {image ? <img src={image} alt={name} /> : <strong>{name}</strong>}
        </button>
      </CardArtworkPreview>
      <p>{face?.type || definition.type}</p>
      <p>{face?.text || definition.text}</p>
      {!kept && (
        <button aria-pressed={selected} disabled={disabled} onClick={select}>
          Bottom {name}
        </button>
      )}
    </article>
  );
}

function BottomOrder({
  cards,
  bottom,
  blocked,
  move,
  remove,
}: {
  cards: Card[];
  bottom: string[];
  blocked: boolean;
  move: (index: number, offset: number) => void;
  remove: (id: string) => void;
}) {
  return (
    <ol aria-label="Bottom order, first card nearer the top">
      {bottom.map((id, index) => {
        const name =
          cards.find((card) => card.id === id)?.definition?.name ?? "card";
        return (
          <li key={id}>
            {name}{" "}
            <button
              aria-label={`Move ${name} earlier`}
              disabled={blocked || index === 0}
              onClick={() => move(index, -1)}
            >
              Earlier
            </button>{" "}
            <button
              aria-label={`Move ${name} later`}
              disabled={blocked || index === bottom.length - 1}
              onClick={() => move(index, 1)}
            >
              Later
            </button>{" "}
            <button
              aria-label={`Remove ${name} from bottom`}
              disabled={blocked}
              onClick={() => remove(id)}
            >
              Remove
            </button>
          </li>
        );
      })}
    </ol>
  );
}
