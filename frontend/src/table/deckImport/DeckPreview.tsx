import type { Draft, GameFormat } from "../../protocol";
import { CommanderSelection } from "./CommanderSelection";

export function DeckPreview({
  format,
  draft,
  commanders,
  onCommandersChange,
  disabled,
  onLoad,
}: {
  format: GameFormat;
  draft: Draft;
  commanders: string[];
  onCommandersChange: (value: string[]) => void;
  disabled: boolean;
  onLoad: () => void;
}) {
  const usesCommanders = format !== "STANDARD";
  return (
    <div className="deck-preview">
      <h3>{draft.count} cards found</h3>
      {usesCommanders && (
        <CommanderSelection
          choices={draft.choices}
          value={commanders}
          onChange={onCommandersChange}
        />
      )}
      <button
        className="primary"
        disabled={
          disabled || (usesCommanders && (!commanders.length || !commanders[0]))
        }
        onClick={onLoad}
      >
        Use this deck
      </button>
    </div>
  );
}
