import {
  useEffect,
  useId,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from "react";
import { artworkPlacement } from "../artworkPlacement";

export function CardArtworkPreview({
  image,
  blocked,
  name,
  children,
}: {
  image?: string;
  blocked: boolean;
  name: string;
  children: ReactNode;
}) {
  const id = useId();
  const root = useRef<HTMLDivElement>(null);
  const tooltip = useRef<HTMLDivElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [open, setOpen] = useState(false);
  const focused = useRef(false);
  const hovered = useRef(false);
  const available = !!image && !blocked;
  const clear = () => clearTimeout(timer.current);
  const dismiss = () => {
    clear();
    setOpen(false);
  };
  const enter = () => {
    clear();
    if (available) timer.current = setTimeout(() => setOpen(true), 350);
  };
  const leave = () => {
    clear();
    if (!focused.current && !hovered.current)
      timer.current = setTimeout(() => setOpen(false), 200);
  };
  usePreviewDismissal(open, available, timer, setOpen);
  usePreviewPosition(open && available, id, root, tooltip, setOpen);
  return (
    <div
      ref={root}
      className="card-preview-anchor"
      onPointerEnter={() => {
        hovered.current = true;
        enter();
      }}
      onPointerLeave={() => {
        hovered.current = false;
        leave();
      }}
      onFocus={() => {
        focused.current = true;
        enter();
      }}
      onBlur={() => {
        focused.current = false;
        leave();
      }}
      onClickCapture={dismiss}
      onContextMenuCapture={dismiss}
      onDragStartCapture={dismiss}
    >
      {children}
      {open && available && (
        <div
          id={id}
          ref={tooltip}
          popover="manual"
          role="tooltip"
          aria-label={`Artwork: ${name}`}
          className="card-artwork-preview"
          onPointerEnter={clear}
          onPointerLeave={leave}
        >
          <img src={image} alt={name} onError={dismiss} />
        </div>
      )}
    </div>
  );
}

function usePreviewDismissal(
  open: boolean,
  available: boolean,
  timer: RefObject<ReturnType<typeof setTimeout> | undefined>,
  setOpen: (value: boolean) => void,
) {
  useEffect(() => {
    const clearPending = () => clearTimeout(timer.current);
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        if (open) {
          event.preventDefault();
          event.stopPropagation();
        }
        clearPending();
        setOpen(false);
      }
    };
    document.addEventListener("keydown", escape, true);
    return () => {
      clearPending();
      document.removeEventListener("keydown", escape, true);
    };
  }, [open, timer, setOpen]);
  useEffect(() => {
    if (!available) {
      clearTimeout(timer.current);
      setOpen(false);
    }
  }, [available, timer, setOpen]);
}

function usePreviewPosition(
  visible: boolean,
  id: string,
  root: RefObject<HTMLDivElement | null>,
  tooltip: RefObject<HTMLDivElement | null>,
  setOpen: (value: boolean) => void,
) {
  useEffect(() => {
    const element = tooltip.current;
    const anchor = root.current?.querySelector("button");
    if (!visible || !element || !anchor) return;
    const position = () => {
      const card = anchor.getBoundingClientRect();
      const controls =
        root.current?.parentElement?.getBoundingClientRect() ?? card;
      const left = Math.min(card.left, controls.left);
      const right = Math.max(card.right, controls.right);
      const top = Math.min(card.top, controls.top);
      const bottom = Math.max(card.bottom, controls.bottom);
      const box = artworkPlacement(
        { left, right, top, bottom, width: right - left, height: bottom - top },
        innerWidth,
        innerHeight,
      );
      if (!box) {
        setOpen(false);
        return;
      }
      Object.assign(element.style, {
        left: `${box.left}px`,
        top: `${box.top}px`,
        width: `${box.width}px`,
        height: `${box.height}px`,
      });
    };
    element.showPopover();
    anchor.setAttribute("aria-describedby", id);
    position();
    let frame: number;
    const follow = () => {
      position();
      frame = requestAnimationFrame(follow);
    };
    frame = requestAnimationFrame(follow);
    return () => {
      anchor.removeAttribute("aria-describedby");
      element.hidePopover();
      cancelAnimationFrame(frame);
    };
  }, [visible, id, root, tooltip, setOpen]);
}
