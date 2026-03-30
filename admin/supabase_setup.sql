-- SafeSphere Admin & Analytics Platform Schema
-- Run this entire script in the Supabase SQL Editor

-- 1. Enable pgcrypto for hashing
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Create target tables
CREATE TABLE IF NOT EXISTS admin_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  supabase_uid UUID NOT NULL UNIQUE, -- links to auth.users
  email TEXT NOT NULL UNIQUE,
  display_name TEXT,
  role TEXT DEFAULT 'admin',
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_hash TEXT NOT NULL UNIQUE,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS analytics_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id TEXT NOT NULL UNIQUE, -- Idempotency key from Android
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  session_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  payload JSONB DEFAULT '{}'::jsonb,
  client_timestamp TIMESTAMPTZ NOT NULL,
  server_timestamp TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS admin_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  subject TEXT NOT NULL,
  body TEXT NOT NULL,
  target_user_id UUID REFERENCES users(id) ON DELETE CASCADE, -- NULL means broadcast to all
  is_critical BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS pending_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE NOT NULL,
  message_id UUID REFERENCES admin_messages(id) ON DELETE CASCADE NOT NULL,
  status TEXT DEFAULT 'pending', -- pending, delivered
  created_at TIMESTAMPTZ DEFAULT now(),
  delivered_at TIMESTAMPTZ,
  UNIQUE(user_id, message_id)
);

CREATE TABLE IF NOT EXISTS revocation_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE NOT NULL,
  reason TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  admin_id UUID REFERENCES admin_accounts(id) ON DELETE SET NULL,
  action TEXT NOT NULL,
  target_user_id UUID,
  details JSONB DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS saved_verifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE NOT NULL,
  incident_session_id TEXT NOT NULL,
  verified_by UUID REFERENCES admin_accounts(id) ON DELETE SET NULL,
  evidence_type TEXT,
  notes TEXT,
  verified_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE(user_id, incident_session_id)
);

-- 3. Row Level Security (RLS) Settings
-- We want ONLY the service_role key to access these tables via the Next.js API.
-- Anon key should NOT have access.
ALTER TABLE admin_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE analytics_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE pending_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE revocation_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE saved_verifications ENABLE ROW LEVEL SECURITY;

-- 4. Initial Indexes for Performance
CREATE INDEX IF NOT EXISTS idx_analytics_user ON analytics_events(user_id);
CREATE INDEX IF NOT EXISTS idx_analytics_session ON analytics_events(session_id);
CREATE INDEX IF NOT EXISTS idx_analytics_type ON analytics_events(event_type);
CREATE INDEX IF NOT EXISTS idx_analytics_time ON analytics_events(client_timestamp);
CREATE INDEX IF NOT EXISTS idx_pending_user_status ON pending_messages(user_id, status);
CREATE INDEX IF NOT EXISTS idx_revocation_user ON revocation_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_logs(created_at DESC);

-- 5. Helper Functions
-- Function to get the latest emergencies
CREATE OR REPLACE FUNCTION get_recent_emergencies(limit_count INT DEFAULT 50)
RETURNS TABLE (
    session_id TEXT,
    user_id UUID,
    start_time TIMESTAMPTZ,
    last_event_time TIMESTAMPTZ,
    trigger_source TEXT,
    sms_count INT,
    call_attempts INT
) AS $$
BEGIN
    RETURN QUERY
    WITH session_summary AS (
        SELECT 
            e.session_id,
            e.user_id,
            MIN(e.client_timestamp) as start_time,
            MAX(e.client_timestamp) as last_event_time,
            MAX(CASE WHEN e.event_type = 'TRIGGER_SOURCE' THEN e.payload->>'source' ELSE NULL END) as trigger_source,
            COUNT(CASE WHEN e.event_type = 'SMS_SENT' THEN 1 ELSE NULL END) as sms_count,
            COUNT(CASE WHEN e.event_type = 'CALL_ATTEMPT' THEN 1 ELSE NULL END) as call_attempts
        FROM analytics_events e
        WHERE e.event_type IN ('TRIGGER_SOURCE', 'SMS_SENT', 'CALL_ATTEMPT', 'LOCATION_SHARED')
        GROUP BY e.session_id, e.user_id
    )
    SELECT * FROM session_summary
    ORDER BY start_time DESC
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
