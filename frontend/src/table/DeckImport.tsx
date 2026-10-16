import { useState } from "react";
import type { Draft, GameFormat } from "../protocol";
import { errorMessage } from "../errors";
import { DeckSourceFields } from "./deckImport/DeckSourceFields";
import { DeckPreview } from "./deckImport/DeckPreview";

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
  function changeText(next: string) {
    setText(next);
    setDraft(null);
  }
  function changeUrl(next: string) {
    setUrl(next);
    setDraft(null);
  }
  async function resolvePreview(): Promise<void> {
    setPending(true);
    try {
      const result = await preview(text, url);
      setDraft(result);
      setCommanders(usesCommanders ? result.suggested : []);
    } catch (error) {
      const message = errorMessage(error);
      if (url && message.toLowerCase().includes("paste")) setUrl("");
      fail(message);
    } finally {
      setPending(false);
    }
  }
  const sourceIsEmpty = !text.trim() && !url.trim();
  return (
    <>
      <DeckSourceFields
        format={format}
        text={text}
        url={url}
        onTextChange={changeText}
        onUrlChange={changeUrl}
      />
      <button
        disabled={disabled || pending || sourceIsEmpty}
        onClick={() => void resolvePreview()}
      >
        {pending ? "Resolving cards…" : "Preview deck"}
      </button>
      {draft && (
        <DeckPreview
          format={format}
          draft={draft}
          commanders={commanders}
          onCommandersChange={setCommanders}
          disabled={disabled}
          onLoad={() => void load(draft, usesCommanders ? commanders : [])}
        />
      )}
    </>
  );
}
