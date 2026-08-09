CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX events_title_trgm_ix ON events USING gin (title gin_trgm_ops);
CREATE INDEX events_body_trgm_ix ON events USING gin (body gin_trgm_ops);

CREATE INDEX applications_applicant_ix ON applications (applicant_id);

CREATE INDEX messages_unread_ix ON messages (conversation_id) WHERE read_at IS NULL;

CREATE INDEX sessions_expires_ix ON sessions (expires_at);
