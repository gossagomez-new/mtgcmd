export function TableHeader({
  room,
  hasView,
  connected,
  fail,
}: {
  room: string;
  hasView: boolean;
  connected: boolean;
  fail: (message: string) => void;
}) {
  return (
    <header className="topbar">
      <a className="brand" href="/">
        ◈{" "}
        <span>
          COMMANDER<span className="brand-light"> TABLE</span>
        </span>
      </a>
      <div className="topbar-right">
        {hasView && (
          <>
            <span className="room-label">
              TABLE {room.slice(0, 6).toUpperCase()}
            </span>
            <button
              className="quiet"
              onClick={() => {
                void navigator.clipboard
                  .writeText(location.href)
                  .catch(() => fail("Copy the URL from your address bar."));
              }}
            >
              Copy invite
            </button>
          </>
        )}
        <span className={`connection ${connected ? "live" : ""}`}>
          {hasView
            ? connected
              ? "Connected"
              : "Reconnecting…"
            : "PRIVATE • FOUR PLAYERS"}
        </span>
      </div>
    </header>
  );
}
