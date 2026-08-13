ALTER TABLE reports
  ADD COLUMN reviewed_at timestamptz,
  ADD COLUMN reviewed_by uuid REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX reports_pending_ix ON reports (event_id) WHERE reviewed_at IS NULL;

COMMENT ON COLUMN reports.reviewed_at IS
  'When a keeper decided about this report (approve or take-down). The queue only lists notes with reports that have none. UNIQUE (event_id, reporter_id) is deliberately untouched: reviewing a report never gives its author a second one.';
