CREATE TABLE scope_members (
  scope_id   uuid NOT NULL REFERENCES scopes(id) ON DELETE CASCADE,
  user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (scope_id, user_id)
);

CREATE INDEX scope_members_user_ix ON scope_members (user_id);
