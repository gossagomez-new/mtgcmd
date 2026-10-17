import { describe, expect, it } from "vitest";
import { actionAvailability } from "./actionAvailability";
import { snapshotFixture } from "./protocolFixtures";

const connections = [
  { connected: true, pending: false, busy: false, blocked: false },
  { connected: false, pending: false, busy: false, blocked: true },
  { connected: true, pending: true, busy: false, blocked: true },
  { connected: true, pending: false, busy: true, blocked: true },
  { connected: false, pending: true, busy: false, blocked: true },
  { connected: false, pending: false, busy: true, blocked: true },
  { connected: true, pending: true, busy: true, blocked: true },
  { connected: false, pending: true, busy: true, blocked: true },
];
const states = [
  { status: "lobby", conceded: false, disabled: false, canPlay: true },
  { status: "lobby", conceded: true, disabled: false, canPlay: false },
  { status: "playing", conceded: false, disabled: false, canPlay: true },
  { status: "playing", conceded: true, disabled: false, canPlay: false },
  { status: "finished", conceded: false, disabled: true, canPlay: false },
  { status: "finished", conceded: true, disabled: true, canPlay: false },
  { status: "paused", conceded: false, disabled: false, canPlay: true },
  { status: "paused", conceded: true, disabled: false, canPlay: false },
];

for (const format of ["COMMANDER", "STANDARD"] as const) {
  describe(`${format} action availability`, () => {
    for (const state of states) {
      it.each(connections)(
        `${state.status}, conceded=${state.conceded}, flags=%j`,
        (flags) => {
          const view = snapshotFixture(format);
          view.status = state.status;
          view.players[0].conceded = state.conceded;
          expect(actionAvailability({ ...flags, view })).toEqual(
            flags.blocked
              ? { disabled: true, canPlay: false }
              : { disabled: state.disabled, canPlay: state.canPlay },
          );
        },
      );
    }
  });
}

it.each(connections)("preserves no-view behavior for flags=%j", (flags) => {
  expect(actionAvailability({ ...flags, view: null })).toEqual({
    disabled: flags.blocked,
    canPlay: !flags.blocked,
  });
});
it.each(connections)(
  "preserves absent-player behavior for flags=%j",
  (flags) => {
    const view = snapshotFixture();
    view.you = 3;
    expect(actionAvailability({ ...flags, view })).toEqual({
      disabled: flags.blocked,
      canPlay: !flags.blocked,
    });
  },
);
it("uses the current player's concession rather than the host's", () => {
  const view = snapshotFixture();
  view.players[0].conceded = true;
  view.players.push({ ...view.players[0], seat: 1, conceded: false });
  view.you = 1;
  expect(
    actionAvailability({ connected: true, pending: false, busy: false, view }),
  ).toEqual({ disabled: false, canPlay: true });
});
