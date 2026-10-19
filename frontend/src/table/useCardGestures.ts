import { useEffect, useRef, type MouseEvent, type DragEvent } from "react";
import type { Card } from "../protocol";
import type { Action, Inspect } from "./types";

export function useCardGestures(
  card: Card,
  inspect: Inspect,
  act: Action,
  disabled: boolean,
) {
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const latest = useRef(card);
  useEffect(() => {
    latest.current = card;
  }, [card]);
  const cancel = () => {
    clearTimeout(timer.current);
    timer.current = undefined;
  };
  useEffect(
    () => () => clearTimeout(timer.current),
    [card.id, card.zone, card.editable, inspect, disabled],
  );
  const canTap = !disabled && card.editable && card.zone === "battlefield";
  return {
    cancelInspection: cancel,
    draggable: !disabled && card.editable,
    onClick(event: MouseEvent<HTMLButtonElement>) {
      cancel();
      if (!canTap || event.detail === 0) inspect(card);
      else timer.current = setTimeout(() => inspect(latest.current), 450);
    },
    onDoubleClick(event: MouseEvent<HTMLButtonElement>) {
      cancel();
      if (!canTap) return;
      event.preventDefault();
      act("TAP", { cardId: card.id });
    },
    onDragStart(event: DragEvent<HTMLButtonElement>) {
      cancel();
      if (disabled || !card.editable) {
        event.preventDefault();
        return;
      }
      event.dataTransfer.setData("application/card", card.id);
      event.dataTransfer.effectAllowed = "move";
    },
  };
}
