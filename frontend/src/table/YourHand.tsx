import type { Snapshot } from "../protocol";
import type { Action, Inspect } from "./types";
import { CardButton } from "./CardViews";
import { useCardDrop } from "./useCardDrop";

export function YourHand({
  view,
  inspect,
  act,
  canPlay,
}: {
  act: Action;
  canPlay: boolean;
  view: Snapshot;
  inspect: Inspect;
}) {
  const drop = useCardDrop(view.cards, "hand", !canPlay, act);
  const me = view.players[view.you];
  return (
    <section
      {...drop}
      className={`hand panel ${drop.className}`}
      title="Drop to the owner’s hand"
    >
      <div className="zone-heading">
        <h3>
          Your hand <span>{me?.counts.hand || 0}</span>
        </h3>
        <span className="private-label">◈ Only you can see these cards</span>
      </div>
      <div className="hand-cards" data-testid="hand">
        {view.cards
          .filter((c) => c.zone === "hand" && c.zoneOwner === view.you)
          .map((c) => (
            <CardButton
              key={c.id}
              card={c}
              inspect={inspect}
              act={act}
              disabled={!canPlay}
            />
          ))}
        {!me?.counts.hand && <p className="empty-state">Your hand is empty.</p>}
      </div>
    </section>
  );
}
