ALTER TABLE notifications DROP COLUMN read_at;

ALTER TABLE events ADD COLUMN expiring_notified_at timestamptz;
