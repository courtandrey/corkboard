-- Denormalized counters (events.score / application_count / report_count,
-- tags.usage_count) maintained by row-level triggers so viewport ranking
-- never joins aggregate subqueries on the hot path.

-- ───────────────────── votes → events.score ─────────────────────

CREATE OR REPLACE FUNCTION trg_votes_score() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE events SET score = score + 1 WHERE id = NEW.event_id;
  ELSIF TG_OP = 'DELETE' THEN
    UPDATE events SET score = score - 1 WHERE id = OLD.event_id;
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER votes_score AFTER INSERT OR DELETE ON votes
  FOR EACH ROW EXECUTE FUNCTION trg_votes_score();

-- ──────────── applications → events.application_count ────────────

CREATE OR REPLACE FUNCTION trg_applications_count() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE events SET application_count = application_count + 1 WHERE id = NEW.event_id;
  ELSIF TG_OP = 'DELETE' THEN
    UPDATE events SET application_count = application_count - 1 WHERE id = OLD.event_id;
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER applications_count AFTER INSERT OR DELETE ON applications
  FOR EACH ROW EXECUTE FUNCTION trg_applications_count();

-- ──────── reports → events.report_count + auto-hide rule ────────

CREATE OR REPLACE FUNCTION trg_reports_count() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE events SET report_count = report_count + 1 WHERE id = NEW.event_id;
    UPDATE events SET status = 'under_review'
    WHERE id = NEW.event_id
      AND status = 'active'
      AND report_count >= 5;   -- REPORT_AUTO_HIDE_THRESHOLD, kept in sync with config
  ELSIF TG_OP = 'DELETE' THEN
    UPDATE events SET report_count = report_count - 1 WHERE id = OLD.event_id;
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER reports_count AFTER INSERT OR DELETE ON reports
  FOR EACH ROW EXECUTE FUNCTION trg_reports_count();

-- ─────────────── event_tags → tags.usage_count ───────────────

CREATE OR REPLACE FUNCTION trg_event_tags_usage() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE tags SET usage_count = usage_count + 1 WHERE id = NEW.tag_id;
  ELSIF TG_OP = 'DELETE' THEN
    UPDATE tags SET usage_count = usage_count - 1 WHERE id = OLD.tag_id;
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER event_tags_usage AFTER INSERT OR DELETE ON event_tags
  FOR EACH ROW EXECUTE FUNCTION trg_event_tags_usage();
