ALTER TABLE events ALTER COLUMN expires_at DROP NOT NULL;

COMMENT ON COLUMN events.expires_at IS 'NULL: stays on the board until the author takes it down';
