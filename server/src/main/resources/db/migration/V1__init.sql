CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS citext;

-- ───────────────────────── identity ─────────────────────────

CREATE TABLE users (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email         citext      UNIQUE NOT NULL,
  display_name  varchar(50) NOT NULL,
  password_hash text,                    -- NULL for Google-only accounts
  google_sub    text        UNIQUE,      -- OpenID Connect `sub` claim
  avatar_seed   text        NOT NULL,    -- deterministic retro avatar
  created_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT users_has_credential
    CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)
);

CREATE TABLE sessions (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash   text UNIQUE NOT NULL,     -- sha256 of the cookie token
  user_agent   text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  expires_at   timestamptz NOT NULL      -- rolling 30 days
);
CREATE INDEX sessions_user_ix ON sessions (user_id);

-- ───────────────────────── events ─────────────────────────

CREATE TYPE event_type AS ENUM
  ('lost_found','activity','club','help','giveaway','happening','notice');

CREATE TYPE event_status AS ENUM
  ('active','resolved','expired','removed','under_review');

CREATE TABLE events (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  author_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type              event_type   NOT NULL,
  status            event_status NOT NULL DEFAULT 'active',
  title             varchar(120) NOT NULL,
  body              text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 4000),
  location          geometry(Point, 4326) NOT NULL,
  applyable         boolean NOT NULL DEFAULT false,
  score             integer NOT NULL DEFAULT 0,   -- denormalized, trigger-maintained
  application_count integer NOT NULL DEFAULT 0,   -- denormalized, trigger-maintained
  report_count      integer NOT NULL DEFAULT 0,   -- denormalized, trigger-maintained
  expires_at        timestamptz NOT NULL,
  resolved_at       timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX events_location_gix ON events USING GIST (location);
CREATE INDEX events_board_ix     ON events (status, expires_at)
  WHERE status = 'active';
CREATE INDEX events_author_ix    ON events (author_id, created_at DESC);

-- ───────────────────────── tags ─────────────────────────

CREATE TABLE tags (
  id          serial PRIMARY KEY,
  name        citext UNIQUE NOT NULL,          -- display form, e.g. "Board Games"
  slug        varchar(40) UNIQUE NOT NULL,     -- "board-games"
  usage_count integer NOT NULL DEFAULT 0,      -- trigger-maintained
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE event_tags (
  event_id uuid    NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  tag_id   integer NOT NULL REFERENCES tags(id)   ON DELETE CASCADE,
  PRIMARY KEY (event_id, tag_id)
);
CREATE INDEX event_tags_tag_ix ON event_tags (tag_id);

-- ─────────────────── engagement & moderation ───────────────────

CREATE TABLE votes (
  user_id    uuid NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
  event_id   uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, event_id)
);
CREATE INDEX votes_event_ix ON votes (event_id);

CREATE TABLE event_hides (
  user_id    uuid NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
  event_id   uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, event_id)
);

CREATE TYPE report_reason AS ENUM ('spam','offensive','scam','danger','other');

CREATE TABLE reports (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id    uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  reporter_id uuid NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
  reason      report_reason NOT NULL,
  detail      varchar(500),
  created_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (event_id, reporter_id)
);

-- ─────────────────── applications & messaging ───────────────────

CREATE TYPE application_status AS ENUM ('pending','accepted','declined','withdrawn');

CREATE TABLE applications (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id     uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  applicant_id uuid NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
  status       application_status NOT NULL DEFAULT 'pending',
  created_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (event_id, applicant_id)
);

CREATE TABLE conversations (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id uuid UNIQUE NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
  event_id       uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  owner_id       uuid NOT NULL REFERENCES users(id),  -- event author at apply time
  applicant_id   uuid NOT NULL REFERENCES users(id),
  created_at     timestamptz NOT NULL DEFAULT now(),
  last_message_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX conversations_owner_ix     ON conversations (owner_id, last_message_at DESC);
CREATE INDEX conversations_applicant_ix ON conversations (applicant_id, last_message_at DESC);

CREATE TABLE messages (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id       uuid NOT NULL REFERENCES users(id),
  body            text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
  created_at      timestamptz NOT NULL DEFAULT now(),
  read_at         timestamptz                       -- read by the *other* party
);
CREATE INDEX messages_conversation_ix ON messages (conversation_id, created_at);

-- ───────────────────────── notifications ─────────────────────────

CREATE TYPE notification_kind AS ENUM
  ('application_received','application_status','message_received',
   'event_expiring','event_under_review');

CREATE TABLE notifications (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind       notification_kind NOT NULL,
  payload    jsonb NOT NULL,   -- { eventId, eventTitle, conversationId?, ... }
  read_at    timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_user_ix ON notifications (user_id, created_at DESC);
