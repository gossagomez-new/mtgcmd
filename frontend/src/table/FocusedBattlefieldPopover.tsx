import { useEffect, useRef, type ReactNode } from "react";

export function FocusedBattlefieldPopover({
  anchor,
  label,
  id,
  close,
  children,
}: {
  anchor: HTMLButtonElement;
  label: string;
  id: string;
  close: () => void;
  children: ReactNode;
}) {
  const popover = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const element = popover.current!;
    element.showPopover();
    const position = () => {
      const origin = anchor.getBoundingClientRect();
      const box = element.getBoundingClientRect();
      const below = origin.bottom + 8;
      const top =
        below + box.height <= innerHeight - 8
          ? below
          : origin.top - box.height - 8;
      element.style.left = `${Math.max(8, Math.min(origin.left, innerWidth - box.width - 8))}px`;
      element.style.top = `${Math.max(8, Math.min(top, innerHeight - box.height - 8))}px`;
    };
    position();
    element
      .querySelector<HTMLButtonElement>("button")
      ?.focus({ preventScroll: true });
    window.addEventListener("resize", position);
    window.addEventListener("scroll", position, true);
    return () => {
      const restore = element.contains(document.activeElement);
      window.removeEventListener("resize", position);
      window.removeEventListener("scroll", position, true);
      element.hidePopover();
      if (restore && anchor.isConnected) anchor.focus({ preventScroll: true });
    };
  }, [anchor]);
  const dismiss = () => {
    if (anchor.isConnected) anchor.focus({ preventScroll: true });
    close();
  };
  return (
    <div
      ref={popover}
      id={id}
      popover="auto"
      role="dialog"
      aria-modal="false"
      aria-label={label}
      className="focused-battlefield panel"
      tabIndex={-1}
      onToggle={(event) => {
        if (event.newState === "closed") close();
      }}
    >
      <button onClick={dismiss}>Close focused battlefield</button>
      {children}
    </div>
  );
}
