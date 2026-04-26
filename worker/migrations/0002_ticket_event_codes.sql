-- counterparty_user_code is included in 0001 for fresh databases.
-- This migration is intentionally kept as a no-op because D1 does not support
-- ALTER TABLE ADD COLUMN IF NOT EXISTS.
SELECT 1;
