-- Tracks how many times the sweeper re-sent the inventory release for an unconfirmed compensation.
ALTER TABLE saga_instance     ADD COLUMN compensation_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE saga_instance_aud ADD COLUMN compensation_attempts INT;
