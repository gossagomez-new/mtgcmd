import { useRef, useState } from "react";
import type { Snapshot } from "../protocol";
import type { Action, Inspect } from "./types";
import { LibrarySelectionFields } from "./LibrarySelectionFields";
import { LibraryInspectionPopover } from "./LibraryInspectionPopover";

export function BoundedLibraryControls({
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
  const [count, setCount] = useState("1");
  const [selected, setSelected] = useState(view.you);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const libraries = view.players.filter(
    (player) => player.privateAccess && !player.conceded,
  );
  const target = libraries.some((player) => player.seat === selected)
    ? selected
    : view.you;
  const available = view.players[target]?.counts.library ?? 0;
  const number = Number(count);
  const disabled =
    !canPlay ||
    view.libraryInspection === undefined ||
    !available ||
    !validCount(number);
  const inspection = view.libraryInspection;
  return (
    <section
      className="bounded-library-controls panel"
      aria-label="Bounded library actions"
    >
      <LibrarySelectionFields
        players={libraries}
        target={target}
        select={setSelected}
        count={count}
        setCount={setCount}
        disabled={!canPlay}
      />
      {(
        [
          ["LOOK_TOP", "Look at top"],
          ["EXILE_TOP", "Exile top"],
          ["SCRY", "Scry"],
        ] as const
      ).map(([command, label]) => (
        <button
          key={command}
          disabled={disabled}
          onClick={(event) => {
            trigger.current = event.currentTarget;
            act(command, { target, count: number });
          }}
        >
          {label}
        </button>
      ))}
      <small>Uses up to the cards remaining. Exile is face-up.</small>
      {inspection && (
        <LibraryInspectionPopover
          key={inspection.id}
          inspection={inspection}
          view={view}
          disabled={!canPlay}
          act={act}
          inspect={inspect}
          trigger={trigger}
        />
      )}
    </section>
  );
}

function validCount(value: number) {
  return Number.isInteger(value) && value >= 1 && value <= 100;
}
