CREATE INDEX sent_notifications_sent_ix ON sent_notifications (sent_at);

COMMENT ON TABLE sent_notifications IS
  'One row per notification the provider took, claimed inside the send transaction so a failed send rolls the claim away. Pruned after 30 days - far beyond Kafka retention, so no redelivery can still arrive for a pruned id.';
