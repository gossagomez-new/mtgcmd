import { useState } from "react";
import type { Draft, GameFormat } from "../protocol";
import { errorMessage } from "../errors";

export function DeckImport({
  format,
  disabled,
  preview,
  load,
  fail,
}: {
  format: GameFormat;
  disabled: boolean;
  preview: (text: string, url: string) => Promise<Draft>;
  load: (draft: Draft, commanders: string[]) => Promise<void>;
  fail: (s: string) => void;
}) {
  const [text, setText] = useState("");
  const [url, setUrl] = useState("");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [commanders, setCommanders] = useState<string[]>([]);
  const [pending, setPending] = useState(false);
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
          onChange={(e) => {
            setUrl(e.target.value);
            setDraft(null);
          }}
          placeholder="https://moxfield.com/decks/…"
        />
      </label>
      <div className="divider">or paste a decklist</div>
      <label>
        Decklist
        <textarea
          rows={8}
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setDraft(null);
          }}
          placeholder={
            usesCommanders
              ? "Commander\n1 Gisa and Geralf\n\nDeck\n1 Sol Ring\n…"
              : "Deck\n24 Island\n4 Card name\n…"
          }
        />
      </label>
      <button
        disabled={disabled || pending || (!text.trim() && !url.trim())}
        onClick={async () => {
          setPending(true);
          try {
            const result = await preview(text, url);
            setDraft(result);
            setCommanders(usesCommanders ? result.suggested : []);
          } catch (e) {
            if (url && errorMessage(e).toLowerCase().includes("paste"))
              setUrl("");
            fail(errorMessage(e));
          } finally {
            setPending(false);
          }
        }}
      >
        {pending ? "Resolving cards…" : "Preview deck"}
      </button>
      {draft && (
        <div className="deck-preview">
          <h3>{draft.count} cards found</h3>
          {usesCommanders && (
            <>
              <label>
                Commander
                <select
                  value={commanders[0] || ""}
                  onChange={(e) =>
                    setCommanders([
                      e.target.value,
                      ...(commanders[1] ? [commanders[1]] : []),
                    ])
                  }
                >
                  <option value="">Choose commander</option>
                  {draft.choices.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Second commander (optional)
                <select
                  value={commanders[1] || ""}
                  onChange={(e) =>
                    setCommanders(
                      [commanders[0] || "", e.target.value].filter(Boolean),
                    )
                  }
                >
                  <option value="">None</option>
                  {draft.choices
                    .filter((c) => c.id !== commanders[0])
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                </select>
              </label>
            </>
          )}
          <button
            className="primary"
            disabled={
              disabled ||
              (usesCommanders && (!commanders.length || !commanders[0]))
            }
            onClick={() => void load(draft, usesCommanders ? commanders : [])}
          >
            Use this deck
          </button>
        </div>
      )}
    </>
  );
}
