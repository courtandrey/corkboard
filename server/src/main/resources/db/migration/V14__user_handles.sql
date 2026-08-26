ALTER TABLE users ADD COLUMN handle citext;

WITH slugged AS (
  SELECT id,
         btrim(left(regexp_replace(lower(display_name), '[^a-z0-9]+', '_', 'g'), 24), '_') AS raw
  FROM users
), based AS (
  SELECT id,
         CASE WHEN length(raw) < 3 THEN 'resident_' || left(replace(id::text, '-', ''), 8) ELSE raw END AS base
  FROM slugged
), numbered AS (
  SELECT id, base, row_number() OVER (PARTITION BY base ORDER BY id) AS rn FROM based
)
UPDATE users u
SET handle = CASE WHEN n.rn = 1 THEN n.base ELSE n.base || n.rn END
FROM numbered n
WHERE u.id = n.id;

ALTER TABLE users
  ALTER COLUMN handle SET NOT NULL,
  ADD CONSTRAINT users_handle_key UNIQUE (handle),
  ADD CONSTRAINT users_handle_shape CHECK (handle::text ~ '^[a-z0-9_]{3,30}$');
