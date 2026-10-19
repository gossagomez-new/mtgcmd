import { useState } from "react";
import { selectionState } from "../permanentSelection";
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
  const [selected, setSelected] = useState<string[]>([]);
  const selection = selectionState(selected, view.cards, !canPlay);
  const toggle = (id: string) =>
    setSelected((ids) =>
      ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id],
    );
  const me = view.players[view.you];
  return (
    <section
      className={`your-field panel ${view.status === "playing" && view.active === view.you ? "active-player" : ""}`}
    >
      {view.status === "playing" && view.active === view.you && (
        <span className="active-turn-label">Active turn</span>
      )}
      <div className="zone-heading">
        <h3>
          Your battlefield <span>{me?.counts.battlefield || 0}</span>
        </h3>
      </div>
      <div
        className="selection-controls"
        role="group"
        aria-label="Permanent selection"
      >
        <span>{selection.count} selected</span>
        <button
          disabled={!selection.valid}
          onClick={() =>
            act("TAP_SELECTED", { cardIds: selected, tapped: true })
          }
        >
          Tap selected
        </button>
        <button
          disabled={!selection.valid}
          onClick={() =>
            act("TAP_SELECTED", { cardIds: selected, tapped: false })
          }
        >
          Untap selected
        </button>
        <button disabled={!selected.length} onClick={() => setSelected([])}>
          Clear selection
        </button>
        {selected.length > 0 && !selection.valid && canPlay && (
          <span>Selection changed. Clear and select cards again.</span>
        )}
      </div>
      <Battlefield
        selection={{ ids: selected, toggle }}
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
