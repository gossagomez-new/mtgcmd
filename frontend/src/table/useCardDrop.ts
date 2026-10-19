import { useEffect, useState, type DragEvent } from "react";
import type { Card } from "../protocol";
import { dropCommand } from "../cardInteractions";
import type { Action } from "./types";

export function useCardDrop(
  cards: readonly Card[],
  zone: string,
  disabled: boolean,
  act: Action,
) {
  const [over, setOver] = useState(false);
  useEffect(() => {
    const end = () => setOver(false);
    window.addEventListener("dragend", end);
    return () => window.removeEventListener("dragend", end);
  }, []);
  return {
    className: over && !disabled ? "drop-active" : "",
    onDragOver(event: DragEvent<HTMLElement>) {
      if (disabled || !event.dataTransfer.types.includes("application/card"))
        return;
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
      setOver(true);
    },
    onDragLeave() {
      setOver(false);
    },
    onDrop(event: DragEvent<HTMLElement>) {
      setOver(false);
      if (disabled) return;
      event.preventDefault();
      event.stopPropagation();
      const data = dropCommand(
        cards,
        event.dataTransfer.getData("application/card"),
        zone,
      );
      if (data) act("MOVE", data);
    },
  };
}
