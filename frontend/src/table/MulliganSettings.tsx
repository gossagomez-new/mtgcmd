import { useState } from "react";
import type { Snapshot } from "../protocol";
import type { Action } from "./types";

export function mulliganSettings(view: Snapshot) {
  return (
    view.mulligans ?? {
      mode: "LONDON" as const,
      freeMulligans: view.format === "STANDARD" ? 0 : 1,
      players: [],
    }
  );
}

export function MulliganSettings({
  view,
  disabled,
  act,
}: {
  view: Snapshot;
  disabled: boolean;
  act: Action;
}) {
  const settings = mulliganSettings(view);
  return (
    <section className="mulligan-settings" aria-label="Mulligan settings">
      <h3>Mulligan rules</h3>
      <p>
        {settings.mode === "LONDON"
          ? `London · ${settings.freeMulligans} free mulligan(s)`
          : "Unlimited seven-card redraws · no bottoming"}
      </p>
      {view.you === view.host && (
        <SettingsForm
          key={`${settings.mode}:${settings.freeMulligans}`}
          mode={settings.mode}
          free={settings.freeMulligans}
          disabled={disabled}
          act={act}
        />
      )}
    </section>
  );
}

function SettingsForm({
  mode: initialMode,
  free: initialFree,
  disabled,
  act,
}: {
  mode: "LONDON" | "UNLIMITED";
  free: number;
  disabled: boolean;
  act: Action;
}) {
  const [mode, setMode] = useState(initialMode);
  const [free, setFree] = useState(String(initialFree));
  const count = Number(free);
  const invalid =
    free.trim() === "" || !Number.isInteger(count) || count < 0 || count > 100;
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        if (!disabled && !invalid)
          act("CONFIGURE_MULLIGANS", { mode, freeMulligans: count });
      }}
    >
      <label htmlFor="mulligan-mode">Mulligan mode</label>
      <select
        id="mulligan-mode"
        value={mode}
        disabled={disabled}
        onChange={(event) =>
          setMode(event.target.value === "UNLIMITED" ? "UNLIMITED" : "LONDON")
        }
      >
        <option value="LONDON">London</option>
        <option value="UNLIMITED">Unlimited redraws</option>
      </select>
      <label>
        Free mulligans (London only)
        <input
          type="number"
          min={0}
          max={100}
          step={1}
          value={free}
          disabled={disabled || mode === "UNLIMITED"}
          onChange={(event) => setFree(event.target.value)}
        />
      </label>
      <p className="hint">Changing these rules resets everyone’s readiness.</p>
      <button disabled={disabled || invalid}>Apply mulligan settings</button>
    </form>
  );
}
