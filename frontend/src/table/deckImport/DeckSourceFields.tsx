import type { GameFormat } from "../../protocol";

export function DeckSourceFields({
  format,
  text,
  url,
  onTextChange,
  onUrlChange,
}: {
  format: GameFormat;
  text: string;
  url: string;
  onTextChange: (text: string) => void;
  onUrlChange: (url: string) => void;
}) {
  const usesCommanders = format !== "STANDARD";
  return (
    <>
      <p className="eyebrow">YOUR DECK</p>
      <h2>
        {usesCommanders ? "Bring your commander." : "Bring your Standard deck."}
      </h2>
      <p className="hint">
        Paste a deck export or import a public Moxfield link.
      </p>
      {!usesCommanders && (
        <p className="hint">
          60–250 main-deck cards. No commanders; sideboards are excluded.
          Legality and copy-limit warnings require your review.
        </p>
      )}
      <label>
        Moxfield URL
        <input
          value={url}
          onChange={(event) => onUrlChange(event.target.value)}
          placeholder="https://moxfield.com/decks/…"
        />
      </label>
      <div className="divider">or paste a decklist</div>
      <label>
        Decklist
        <textarea
          rows={8}
          value={text}
          onChange={(event) => onTextChange(event.target.value)}
          placeholder={
            usesCommanders
              ? "Commander\n1 Gisa and Geralf\n\nDeck\n1 Sol Ring\n…"
              : "Deck\n24 Island\n4 Card name\n…"
          }
        />
      </label>
    </>
  );
}
