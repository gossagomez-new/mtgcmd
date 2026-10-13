CREATE TABLE guests (
    token_hash TEXT PRIMARY KEY,
    csrf_token TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE rooms (
    id TEXT PRIMARY KEY,
    snapshot JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE members (
    room_id TEXT NOT NULL REFERENCES rooms(id),
    guest_hash TEXT NOT NULL REFERENCES guests(token_hash),
    seat INTEGER NOT NULL CHECK (seat BETWEEN 0 AND 3),
    PRIMARY KEY (room_id, seat),
    UNIQUE (room_id, guest_hash)
);
CREATE TABLE commands (
    room_id TEXT NOT NULL REFERENCES rooms(id),
    seat INTEGER NOT NULL,
    command_id TEXT NOT NULL,
    digest TEXT NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (room_id, seat, command_id)
);
CREATE TABLE actions (
    room_id TEXT NOT NULL REFERENCES rooms(id),
    revision BIGINT NOT NULL,
    seat INTEGER NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, revision)
);
CREATE TABLE catalog (
    name TEXT PRIMARY KEY,
    definition JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
