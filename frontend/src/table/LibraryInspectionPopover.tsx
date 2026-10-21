import type { RefObject } from "react";
import { useScryDraft } from "./useScryDraft";
import { useInspectionPopover } from "./useInspectionPopover";
import type { LibraryInspection, Snapshot } from "../protocol";
import type { Action, Inspect } from "./types";
import { ScryCardGroup } from "./ScryCardGroup";

type InspectionProps = {
  inspection: LibraryInspection;
  view: Snapshot;
  disabled: boolean;
  act: Action;
  inspect: Inspect;
  trigger: RefObject<HTMLButtonElement | null>;
};

export function LibraryInspectionPopover({
  inspection,
  view,
  disabled,
  act,
  inspect,
  trigger,
}: InspectionProps) {
  const close = () => {
    if (!disabled) act("CLOSE_INSPECTION", { inspectionId: inspection.id });
  };
  const popover = useInspectionPopover(trigger, close);
  const { draft, cards, valid, move, reorder } = useScryDraft(
    inspection,
    view.cards,
  );
  return (
    <div
      ref={popover}
      popover="manual"
      role="dialog"
      aria-modal="false"
      aria-label={
        inspection.mode === "scry" ? "Scry library" : "Look at top cards"
      }
      className="library-inspection panel"
    >
      <header className="zone-heading">
        <h2>
          {inspection.mode === "scry" ? "Scry" : "Look"} ·{" "}
          {view.players[inspection.target]?.name}
        </h2>
        <button disabled={disabled} onClick={close}>
          {inspection.mode === "scry" ? "Cancel scry" : "Close inspection"}
        </button>
      </header>
      <p>
        Private inspection of {inspection.cardIds.length} top card(s).{" "}
        {inspection.mode === "scry"
          ? "Arrange both groups, then confirm. The rest of the library stays in order."
          : "Look only. Closing does not change library order."}
      </p>
      {!valid && (
        <p role="alert">
          Inspection changed. Close it and inspect the library again.
        </p>
      )}
      <div className="scry-groups">
        {(["top", "bottom"] as const)
          .filter((group) => group === "top" || inspection.mode === "scry")
          .map((group) => (
            <ScryCardGroup
              key={group}
              group={group}
              ids={draft[group]}
              cards={cards}
              scry={inspection.mode === "scry"}
              disabled={disabled || !valid}
              inspect={inspect}
              act={act}
              move={move}
              reorder={reorder}
            />
          ))}
      </div>
      {inspection.mode === "scry" && (
        <button
          className="primary"
          disabled={disabled || !valid}
          onClick={() =>
            act("APPLY_SCRY", { inspectionId: inspection.id, ...draft })
          }
        >
          Confirm scry
        </button>
      )}
    </div>
  );
}
