import type { Snapshot } from "./protocol";

const namesFormat = new Intl.ListFormat("en", {
  style: "long",
  type: "conjunction",
});

export function openingHandWaitingMessage(view: Snapshot): string {
  const names = view.players
    .filter(
      (player) =>
        player.seat !== view.you &&
        view.mulligans?.players.some(
          (progress) => progress.seat === player.seat && !progress.kept,
        ),
    )
    .map((player) => player.name);
  if (names.length === 0) return "Waiting for the game to begin.";
  return `Waiting for ${namesFormat.format(names)} to keep their opening ${names.length === 1 ? "hand" : "hands"}.`;
}
