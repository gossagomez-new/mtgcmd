import type { Player } from "../protocol";

export function LibrarySelectionFields({
  players,
  target,
  select,
  count,
  setCount,
  disabled,
}: {
  players: Player[];
  target: number;
  select: (seat: number) => void;
  count: string;
  setCount: (count: string) => void;
  disabled: boolean;
}) {
  return (
    <>
      <label>
        Library owner
        <select
          value={target}
          disabled={disabled}
          onChange={(event) => select(Number(event.target.value))}
        >
          {players.map((player) => (
            <option key={player.seat} value={player.seat}>
              {player.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Number of top cards
        <input
          type="number"
          min="1"
          max="100"
          value={count}
          onChange={(event) => setCount(event.target.value)}
          disabled={disabled}
        />
      </label>
    </>
  );
}
