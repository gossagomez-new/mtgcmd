import type { Snapshot } from "./protocol";

export function actionAvailability({
  connected,
  pending,
  busy,
  view,
}: {
  connected: boolean;
  pending: boolean;
  busy: boolean;
  view: Snapshot | null;
}) {
  const disabled = !connected || pending || busy || view?.status === "finished";
  return {
    disabled,
    canPlay:
      !disabled &&
      view?.status !== "opening" &&
      !view?.players[view.you]?.conceded,
  };
}
