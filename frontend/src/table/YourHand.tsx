import type { Snapshot } from "../protocol";
import type { Inspect } from "./types";
import { CardButton } from "./CardViews";

export function YourHand({
  view,
  inspect,
}: {
  view: Snapshot;
  inspect: Inspect;
}) {
  const me = view.players[view.you];
  return (
    <section className="hand panel">
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
            <CardButton key={c.id} card={c} inspect={inspect} />
          ))}
        {!me?.counts.hand && <p className="empty-state">Your hand is empty.</p>}
      </div>
    </section>
  );
}
