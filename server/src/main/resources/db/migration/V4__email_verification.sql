ALTER TABLE users ADD COLUMN email_verified_at timestamptz;

UPDATE users SET email_verified_at = created_at;

CREATE TABLE email_verifications (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  text        NOT NULL UNIQUE,
  expires_at  timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX email_verifications_user_ix ON email_verifications (user_id, created_at DESC);

COMMENT ON COLUMN users.email_verified_at IS
  'Until this is set the account is read-only. Accounts that predate verification are treated as confirmed so nobody is locked out by the migration.';
