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
        <button disabled={!canPlay} onClick={() => act("UNTAP_ALL")}>
          Untap all
        </button>
      </div>
      <Battlefield
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
        <span>Drag cards onto the battlefield or use their action menu</span>
      </div>
    </section>
  );
}
