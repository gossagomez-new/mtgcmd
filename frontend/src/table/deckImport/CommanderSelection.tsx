import type { CardDefinition } from "../../protocol";

export function CommanderSelection({
  choices,
  value,
  onChange,
}: {
  choices: CardDefinition[];
  value: string[];
  onChange: (value: string[]) => void;
}) {
  return (
    <>
      <label>
        Commander
        <select
          value={value[0] || ""}
          onChange={(e) =>
            onChange([e.target.value, ...(value[1] ? [value[1]] : [])])
          }
        >
          <option value="">Choose commander</option>
          {choices.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Second commander (optional)
        <select
          value={value[1] || ""}
          onChange={(e) =>
            onChange([value[0] || "", e.target.value].filter(Boolean))
          }
        >
          <option value="">None</option>
          {choices
            .filter((c) => c.id !== value[0])
            .map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
        </select>
      </label>
    </>
  );
}
