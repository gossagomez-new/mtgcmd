import type { Snapshot } from "../protocol";
import type { Inspect } from "./types";
import { CardButton } from "./CardViews";

export function SharedCards({
  view,
  inspect,
}: {
  view: Snapshot;
  inspect: Inspect;
}) {
  return (
    <>
      {view.cards.some(
        (c) => ["hand", "library"].includes(c.zone) && c.zoneOwner !== view.you,
      ) && (
        <section className="panel reveals">
          <h3>Revealed / shared cards</h3>
          <div className="hand-cards">
            {view.cards
              .filter(
                (c) =>
                  ["hand", "library"].includes(c.zone) &&
                  c.zoneOwner !== view.you,
              )
              .map((c) => (
                <CardButton key={c.id} card={c} inspect={inspect} />
              ))}
          </div>
        </section>
      )}
    </>
  );
}
