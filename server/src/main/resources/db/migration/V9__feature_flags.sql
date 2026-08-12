CREATE TABLE feature_flags (
  key        text PRIMARY KEY,
  enabled    boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid REFERENCES users(id) ON DELETE SET NULL
);

COMMENT ON TABLE feature_flags IS
  'State only. Which flags exist and what they mean is a release decision (app.corkboard.features.FeatureFlag), and their copy lives in messages.properties — a row here says nothing but whether a known flag is currently on. Rows for flags this build does not know are ignored.';

INSERT INTO feature_flags (key, enabled) VALUES
  ('ARE_USER_DETAILS_EDITABLE', true);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'FEATURE_FLAG_MANAGE' FROM roles WHERE key = 'admin';
