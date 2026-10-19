import type { Snapshot } from "../protocol";
import type { Action, Inspect } from "./types";
import { Battlefield } from "./CardViews";

export function YourBattlefield({
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
  const me = view.players[view.you];
  return (
    <section className="your-field panel">
      <div className="zone-heading">
        <h3>
          Your battlefield <span>{me?.counts.battlefield || 0}</span>
        </h3>
      </div>
      <Battlefield
        allCards={view.cards}
        cards={view.cards.filter(
          (c) => c.zone === "battlefield" && c.controller === view.you,
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
            const text = prompt("Power/toughness and rules text", "1/1") || "";
            act("TOKEN", { name, text, type: "Creature — Token" });
          }}
        >
          + Create token
        </button>
        <span>
          Double-click to tap. Drag cards between zones or inspect for more
          actions.
        </span>
      </div>
    </section>
  );
}
