import { useEffect, useRef, type RefObject } from "react";

export function useInspectionPopover(
  trigger: RefObject<HTMLButtonElement | null>,
  close: () => void,
) {
  const popover = useRef<HTMLDivElement>(null);
  const cancel = useRef(close);
  useEffect(() => {
    cancel.current = close;
  }, [close]);
  useEffect(() => {
    const element = popover.current!;
    const anchor = trigger.current;
    const dismiss = () => {
      if (!document.querySelector("dialog[open]")) cancel.current();
    };
    const pointer = (event: PointerEvent) => {
      if (event.target instanceof Node && !element.contains(event.target))
        dismiss();
    };
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !document.querySelector("dialog[open]")) {
        event.preventDefault();
        event.stopPropagation();
        dismiss();
      }
    };
    element.showPopover();
    element
      .querySelector<HTMLButtonElement>("button")
      ?.focus({ preventScroll: true });
    document.addEventListener("pointerdown", pointer);
    document.addEventListener("keydown", keyboard, true);
    return () => {
      document.removeEventListener("pointerdown", pointer);
      document.removeEventListener("keydown", keyboard, true);
      const restore =
        element.contains(document.activeElement) ||
        document.activeElement === document.body;
      element.hidePopover();
      if (restore && anchor?.isConnected) anchor.focus({ preventScroll: true });
    };
  }, [trigger]);
  return popover;
}
