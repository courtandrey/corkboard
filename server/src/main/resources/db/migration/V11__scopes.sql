CREATE TYPE scope_kind AS ENUM ('global', 'personal');

CREATE TABLE scopes (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  kind       scope_kind NOT NULL,
  owner_id   uuid REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT scopes_owner_rule CHECK ((kind = 'global') = (owner_id IS NULL))
);

CREATE UNIQUE INDEX scopes_one_global_ix ON scopes (kind) WHERE kind = 'global';
CREATE UNIQUE INDEX scopes_owner_ix      ON scopes (owner_id) WHERE kind = 'personal';

COMMENT ON TABLE scopes IS
  'One board per row. Exactly one global scope, and at most one personal scope per account, created the first time that account writes to it. Who may read a scope is decided in one place (ScopeService.requireReadable): today the owner, tomorrow whoever the owner lets in.';

INSERT INTO scopes (id, kind) VALUES ('00000000-0000-0000-0000-0000000000b0', 'global');

ALTER TABLE events ADD COLUMN scope_id uuid REFERENCES scopes(id) ON DELETE CASCADE;
UPDATE events SET scope_id = '00000000-0000-0000-0000-0000000000b0';
ALTER TABLE events ALTER COLUMN scope_id SET NOT NULL;

CREATE EXTENSION IF NOT EXISTS btree_gist;
DROP INDEX events_location_gix;
CREATE INDEX events_scope_location_gix ON events USING GIST (scope_id, location);

CREATE OR REPLACE FUNCTION trg_event_tags_usage() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE tags SET usage_count = usage_count + 1
    WHERE id = NEW.tag_id
      AND EXISTS (
        SELECT 1 FROM events e JOIN scopes s ON s.id = e.scope_id
        WHERE e.id = NEW.event_id AND s.kind = 'global'
      );
  ELSIF TG_OP = 'DELETE' THEN
    UPDATE tags SET usage_count = usage_count - 1
    WHERE id = OLD.tag_id
      AND NOT EXISTS (
        SELECT 1 FROM events e JOIN scopes s ON s.id = e.scope_id
        WHERE e.id = OLD.event_id AND s.kind <> 'global'
      );
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;
