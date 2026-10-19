import { useEffect, useRef, type ReactNode } from "react";

export function Modal({
  label,
  className = "",
  close,
  children,
}: {
  label: string;
  className?: string;
  close: () => void;
  children: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current!;
    const trigger = document.activeElement;
    element.showModal();
    return () => {
      element.close();
      if (trigger instanceof HTMLElement && trigger.isConnected)
        trigger.focus();
    };
  }, []);
  return (
    <dialog
      ref={dialog}
      aria-label={label}
      className={`table-modal panel ${className}`}
      onCancel={(event) => {
        event.preventDefault();
        event.stopPropagation();
        close();
      }}
    >
      {children}
    </dialog>
  );
}
