import type { Snapshot } from "../protocol";
import type { Action, Inspect, OpenZone } from "./types";
import { OpponentBoards } from "./OpponentBoards";
import { TurnControls } from "./TurnControls";
import { GameStack } from "./GameStack";
import { YourBattlefield } from "./YourBattlefield";
import { YourResources } from "./YourResources";
import { YourHand } from "./YourHand";
import { SharedCards } from "./SharedCards";
import { GameSidebar } from "./GameSidebar";

export function PlayingTable({
  view,
  canPlay,
  disabled,
  act,
  inspect,
  openZone,
  socketOnline,
}: {
  view: Snapshot;
  canPlay: boolean;
  disabled: boolean;
  act: Action;
  inspect: Inspect;
  openZone: OpenZone;
  socketOnline: number[];
}) {
  return (
    <main className="game-layout">
      <div className="table-area">
        <div className="board-scroll">
          <OpponentBoards
            view={view}
            online={socketOnline}
            inspect={inspect}
            openZone={openZone}
          />
          <TurnControls view={view} canPlay={canPlay} act={act} />
          <GameStack
            view={view}
            canPlay={canPlay}
            act={act}
            inspect={inspect}
          />
          <YourBattlefield
            view={view}
            canPlay={canPlay}
            act={act}
            inspect={inspect}
          />
        </div>
        <YourResources
          view={view}
          canPlay={canPlay}
          act={act}
          openZone={openZone}
        />
        <YourHand view={view} inspect={inspect} />
        <SharedCards view={view} inspect={inspect} />
      </div>
      <GameSidebar
        view={view}
        canPlay={canPlay}
        disabled={disabled}
        act={act}
      />
    </main>
  );
}
