import { useState } from "react";
import type { Action } from "./types";
import type { Card } from "../protocol";
import { CardButton } from "./CardViews";

export function ZoneViewer({
  cards,
  seat,
  zone,
  playerName,
  inspect,
  close,
  act,
  disabled,
}: {
  act: Action;
  disabled: boolean;
  cards: Card[];
  seat: number;
  zone: string;
  playerName: string;
  inspect: (card: Card) => void;
  close: () => void;
}) {
  const [query, setQuery] = useState("");
  const zoneCards = cards.filter(
    (card) => card.zone === zone && card.zoneOwner === seat,
  );
  const normalizedQuery = query.trim().toLowerCase();
  const matches = zoneCards
    .map((card, index) => ({ card, position: index + 1 }))
    .filter(({ card }) => {
      if (zone !== "library" || !normalizedQuery) return true;
      const names = card.definition
        ? [
            card.definition.name,
            ...card.definition.faces.map((face) => face.name),
          ]
        : ["Face-down card"];
      return names.some((name) => name.toLowerCase().includes(normalizedQuery));
    });
  return (
    <div className="modal-shade">
      <section
        className="zone-modal panel"
        role="dialog"
        aria-label="Zone viewer"
      >
        <div className="zone-heading">
          <h2>
            {playerName} / {zone}
          </h2>
          <button aria-label="Close zone viewer" onClick={close}>
            ×
          </button>
        </div>
        {zone === "library" && (
          <>
            <p className="hint">
              Private search. Close this view before shuffling. Use card actions
              to reorder cards to the top or bottom.
            </p>
            <div className="library-search">
              <label>
                Search library cards
                <input
                  type="search"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Card name or either face…"
                />
              </label>
              <button onClick={() => setQuery("")} disabled={!query}>
                Clear search
              </button>
            </div>
            <p className="hint" role="status">
              Showing {matches.length} of {zoneCards.length} cards.
            </p>
          </>
        )}
        <div className="zone-grid">
          {matches.map(({ card, position }) => (
            <div key={card.id}>
              {zone === "library" && <small>#{position}</small>}
              <CardButton
                card={card}
                inspect={inspect}
                act={act}
                disabled={disabled}
              />
            </div>
          ))}
        </div>
        {zoneCards.length === 0 ? (
          <p className="empty-state">No visible cards in this zone.</p>
        ) : (
          matches.length === 0 && (
            <p className="empty-state">No cards match your search.</p>
          )
        )}
      </section>
    </div>
  );
}
