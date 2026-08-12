ALTER TYPE event_status ADD VALUE IF NOT EXISTS 'taken_down';

ALTER TYPE notification_kind ADD VALUE IF NOT EXISTS 'event_taken_down';
