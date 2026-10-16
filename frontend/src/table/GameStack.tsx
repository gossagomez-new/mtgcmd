import type { Snapshot } from "../protocol";
import type { Action, Inspect } from "./types";
import { CardButton } from "./CardViews";

export function GameStack({
  view,
  canPlay,
  act,
  inspect,
}: {
  view: Snapshot;
  canPlay: boolean;
  act: Action;
  inspect: Inspect;
}) {
  return (
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
                    onClick={() => act("RESOLVE_ABILITY", { abilityId: a.id })}
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
  );
}
