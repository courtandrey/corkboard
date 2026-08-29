CREATE TYPE connection_status AS ENUM ('pending', 'accepted', 'declined');

CREATE TABLE connections (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  requester_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  addressee_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status       connection_status NOT NULL DEFAULT 'pending',
  created_at   timestamptz NOT NULL DEFAULT now(),
  answered_at  timestamptz,
  CONSTRAINT connections_two_people CHECK (requester_id <> addressee_id)
);

CREATE UNIQUE INDEX connections_pair_ux
  ON connections (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

CREATE INDEX connections_addressee_ix ON connections (addressee_id, status);
CREATE INDEX connections_requester_ix ON connections (requester_id, status);

ALTER TYPE notification_kind ADD VALUE IF NOT EXISTS 'connection_requested';
ALTER TYPE notification_kind ADD VALUE IF NOT EXISTS 'connection_accepted';

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'CONNECTION_MANAGE'
FROM roles r
WHERE r.key = 'verified_resident';

CREATE INDEX users_display_name_trgm_ix ON users USING gin (display_name gin_trgm_ops);
CREATE INDEX users_handle_trgm_ix ON users USING gin ((handle::text) gin_trgm_ops);
