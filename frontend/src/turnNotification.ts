import type { Snapshot } from "./protocol";
export type NoticeStorage = Pick<Storage, "getItem" | "setItem">;

export function turnNotice(
  view: Pick<
    Snapshot,
    "id" | "you" | "turn" | "turnVisit" | "active" | "status"
  >,
  storage: NoticeStorage,
): string {
  if (view.status !== "playing" || view.active !== view.you) return "";
  const visit = view.turnVisit ?? view.turn;
  const key = `mtgcmd:turn-notice:${view.id}:${view.you}${view.turnVisit === undefined ? "" : ":visits"}`;
  const previous = Number(storage.getItem(key) ?? -1);
  if (Number.isFinite(previous) && previous >= visit) return "";
  storage.setItem(key, String(visit));
  return `Your turn — turn ${view.turn}`;
}
