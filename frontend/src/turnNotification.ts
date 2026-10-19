import type { Snapshot } from "./protocol";
export type NoticeStorage = Pick<Storage, "getItem" | "setItem">;

export function turnNotice(
  view: Pick<Snapshot, "id" | "you" | "turn" | "active" | "status">,
  storage: NoticeStorage,
): string {
  if (view.status !== "playing" || view.active !== view.you) return "";
  const key = `mtgcmd:turn-notice:${view.id}:${view.you}`;
  const previous = Number(storage.getItem(key) ?? -1);
  if (Number.isFinite(previous) && previous >= view.turn) return "";
  storage.setItem(key, String(view.turn));
  return `Your turn — turn ${view.turn}`;
}
