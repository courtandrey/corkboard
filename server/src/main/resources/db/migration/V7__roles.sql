CREATE TABLE roles (
  id          serial PRIMARY KEY,
  key         text UNIQUE NOT NULL,
  description text NOT NULL
);

CREATE TABLE role_permissions (
  role_id    integer NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission text    NOT NULL,
  PRIMARY KEY (role_id, permission)
);

CREATE TABLE user_roles (
  user_id    uuid    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id    integer NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  granted_at timestamptz NOT NULL DEFAULT now(),
  granted_by uuid REFERENCES users(id) ON DELETE SET NULL,
  PRIMARY KEY (user_id, role_id)
);
CREATE INDEX user_roles_role_ix ON user_roles (role_id);

INSERT INTO roles (key, description) VALUES
  ('resident',          'Everyone signed in. Can read the board and curate their own view of it.'),
  ('verified_resident', 'Held automatically once the address is confirmed. Everything that puts something in front of other people.'),
  ('moderator',         'Can take any note off the board and work the report queue.'),
  ('admin',             'A moderator who can also hand out roles.');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
JOIN (VALUES
  ('resident',          'EVENT_HIDE'),
  ('verified_resident', 'EVENT_CREATE'),
  ('verified_resident', 'EVENT_VOTE'),
  ('verified_resident', 'EVENT_REPORT'),
  ('verified_resident', 'EVENT_APPLY'),
  ('verified_resident', 'MESSAGE_SEND'),
  ('moderator',         'EVENT_HIDE'),
  ('moderator',         'EVENT_TAKE_DOWN_ANY'),
  ('moderator',         'REPORT_QUEUE_VIEW'),
  ('admin',             'EVENT_HIDE'),
  ('admin',             'EVENT_TAKE_DOWN_ANY'),
  ('admin',             'REPORT_QUEUE_VIEW'),
  ('admin',             'ROLE_MANAGE')
) AS p(role_key, permission) ON p.role_key = r.key;

COMMENT ON TABLE user_roles IS
  'Explicitly granted roles. "resident" and "verified_resident" are NOT stored here: the first is held by every signed-in account and the second follows users.email_verified_at, so neither can drift from the thing it describes. Everything an administrator hands out lives here.';
