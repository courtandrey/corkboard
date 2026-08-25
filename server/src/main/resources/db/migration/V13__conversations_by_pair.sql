ALTER TABLE messages ADD COLUMN event_id uuid REFERENCES events(id) ON DELETE SET NULL;

UPDATE messages m
SET event_id = c.event_id
FROM conversations c
WHERE m.conversation_id = c.id
  AND m.id = (
    SELECT m2.id FROM messages m2
    WHERE m2.conversation_id = c.id
    ORDER BY m2.created_at, m2.id
    LIMIT 1
  );

ALTER TABLE conversations
  ADD COLUMN user_a_id uuid REFERENCES users(id) ON DELETE CASCADE,
  ADD COLUMN user_b_id uuid REFERENCES users(id) ON DELETE CASCADE;

-- the pair is stored in one canonical order, so it can be a key
UPDATE conversations
SET user_a_id = least(owner_id, applicant_id),
    user_b_id = greatest(owner_id, applicant_id);

CREATE TEMP TABLE conversation_merge ON COMMIT DROP AS
SELECT id,
       first_value(id) OVER (PARTITION BY user_a_id, user_b_id ORDER BY created_at, id) AS keeper
FROM conversations;

UPDATE messages m
SET conversation_id = k.keeper
FROM conversation_merge k
WHERE m.conversation_id = k.id AND k.keeper <> k.id;

UPDATE notifications n
SET payload = jsonb_set(n.payload, '{conversationId}', to_jsonb(k.keeper::text))
FROM conversation_merge k
WHERE k.keeper <> k.id AND n.payload ->> 'conversationId' = k.id::text;

DELETE FROM conversations c
USING conversation_merge k
WHERE c.id = k.id AND k.keeper <> k.id;

ALTER TABLE conversations
  DROP COLUMN application_id,
  DROP COLUMN event_id,
  DROP COLUMN owner_id,
  DROP COLUMN applicant_id,
  ALTER COLUMN user_a_id SET NOT NULL,
  ALTER COLUMN user_b_id SET NOT NULL,
  ADD CONSTRAINT conversations_pair_order CHECK (user_a_id < user_b_id),
  ADD CONSTRAINT conversations_pair_key UNIQUE (user_a_id, user_b_id);

UPDATE conversations c
SET last_message_at = COALESCE(
  (SELECT max(m.created_at) FROM messages m WHERE m.conversation_id = c.id),
  c.created_at
);

CREATE INDEX conversations_user_a_ix ON conversations (user_a_id, last_message_at DESC, id DESC);
CREATE INDEX conversations_user_b_ix ON conversations (user_b_id, last_message_at DESC, id DESC);
CREATE INDEX messages_event_ix ON messages (event_id) WHERE event_id IS NOT NULL;
