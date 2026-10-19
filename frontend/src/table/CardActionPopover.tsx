import { useEffect, useRef, type KeyboardEvent, type ReactNode } from "react";

function navigate(event: KeyboardEvent<HTMLDivElement>, close: () => void) {
  if (event.key === "Escape") {
    event.preventDefault();
    event.stopPropagation();
    close();
    return;
  }
  if (event.key === "Tab") {
    close();
    return;
  }
  const buttons = Array.from(
    event.currentTarget.querySelectorAll<HTMLButtonElement>(
      "button:not(:disabled)",
    ),
  );
  const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
  const positions: Record<string, number> = {
    ArrowDown: (index + 1) % buttons.length,
    ArrowUp: (index - 1 + buttons.length) % buttons.length,
    Home: 0,
    End: buttons.length - 1,
  };
  if (event.key in positions) {
    event.preventDefault();
    buttons[positions[event.key]]?.focus();
  }
}

export function CardActionPopover({
  anchor,
  close,
  children,
}: {
  anchor: HTMLElement;
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
      const left =
        origin.right + box.width + 16 <= innerWidth
          ? origin.right + 8
          : origin.left - box.width - 8;
      element.style.left = `${Math.max(8, Math.min(left, innerWidth - box.width - 8))}px`;
      element.style.top = `${Math.max(8, Math.min(origin.top, innerHeight - box.height - 8))}px`;
    };
    position();
    element.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus();
    window.addEventListener("resize", position);
    window.addEventListener("scroll", position, true);
    return () => {
      const restore = element.contains(document.activeElement);
      window.removeEventListener("resize", position);
      window.removeEventListener("scroll", position, true);
      element.hidePopover();
      if (restore && anchor.isConnected) anchor.focus();
    };
  }, [anchor]);
  return (
    <div
      ref={popover}
      popover="auto"
      role="menu"
      tabIndex={-1}
      aria-label="Card actions"
      className="card-action-menu panel"
      onKeyDown={(event) =>
        navigate(event, () => {
          if (anchor.isConnected) anchor.focus();
          close();
        })
      }
      onToggle={(event) => {
        if (event.newState === "closed") close();
      }}
    >
      {children}
    </div>
  );
}
