import type { Card } from "../protocol";
import type { Action, Inspect } from "./types";
import { CardButton } from "./CardViews";

type Group = "top" | "bottom";
export function ScryCardGroup({
  group,
  ids,
  cards,
  scry,
  disabled,
  inspect,
  act,
  move,
  reorder,
}: {
  group: Group;
  ids: string[];
  cards: Card[];
  scry: boolean;
  disabled: boolean;
  inspect: Inspect;
  act: Action;
  move: (id: string, group: Group) => void;
  reorder: (group: Group, index: number, delta: number) => void;
}) {
  return (
    <section
      role="group"
      aria-label={group === "top" ? "Keep on top" : "Put on bottom"}
    >
      <h3>
        {group === "top"
          ? "Keep on top · first card is topmost"
          : "Put on bottom · last card is bottommost"}
      </h3>
      <ol className="scry-card-list">
        {ids.map((id, index) => {
          const card = cards.find((value) => value.id === id);
          if (!card) return null;
          const name = cardLabel(card);
          const destination = group === "top" ? "bottom" : "top";
          return (
            <li key={id}>
              <CardButton card={card} inspect={inspect} act={act} disabled />
              {scry && (
                <div className="scry-card-controls">
                  <button
                    disabled={disabled}
                    onClick={() => move(id, destination)}
                  >
                    Move {name} to {destination}
                  </button>
                  <button
                    disabled={disabled || index === 0}
                    onClick={() => reorder(group, index, -1)}
                  >
                    Move {name} earlier
                  </button>
                  <button
                    disabled={disabled || index === ids.length - 1}
                    onClick={() => reorder(group, index, 1)}
                  >
                    Move {name} later
                  </button>
                </div>
              )}
            </li>
          );
        })}
      </ol>
      {!ids.length && <p>No cards in this group.</p>}
    </section>
  );
}

function cardLabel(card: Card) {
  return (
    card.definition?.faces[card.face]?.name ||
    card.definition?.name ||
    "Face-down card"
  );
}
