CREATE TABLE deck_drafts (
    id TEXT PRIMARY KEY,
    guest_hash TEXT NOT NULL REFERENCES guests(token_hash),
    cards JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
