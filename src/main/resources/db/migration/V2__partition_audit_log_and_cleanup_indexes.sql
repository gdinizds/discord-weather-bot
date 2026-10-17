SET search_path TO weather_bot;

-- 1. Remove redundant indexes covered by UNIQUE constraints
DROP INDEX IF EXISTS weather_bot.idx_user_weather_config_user_id;
DROP INDEX IF EXISTS weather_bot.idx_weather_sessions_session_id;

-- 2. Configure fillfactor for HOT updates on sessions table
ALTER TABLE weather_bot.weather_sessions SET (fillfactor = 85);

-- 3. Setup pg_partman schema and extension
CREATE SCHEMA IF NOT EXISTS partman;
CREATE EXTENSION IF NOT EXISTS pg_partman SCHEMA partman;

-- 4. Migrate weather_audit_log to range-partitioned table by created_at
ALTER TABLE weather_bot.weather_audit_log RENAME TO weather_audit_log_old;

DROP INDEX IF EXISTS weather_bot.idx_weather_audit_correlation_id;
DROP INDEX IF EXISTS weather_bot.idx_weather_audit_user_id;
DROP INDEX IF EXISTS weather_bot.idx_weather_audit_created_at;

CREATE TABLE weather_bot.weather_audit_log (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    correlation_id    VARCHAR(64)  NOT NULL,
    guild_id          VARCHAR(32),
    channel_id        VARCHAR(32),
    user_id           VARCHAR(32)  NOT NULL,
    command_name      VARCHAR(32)  NOT NULL,
    trigger_type      VARCHAR(16)  NOT NULL,
    query_location    VARCHAR(256),
    resolved_location VARCHAR(256),
    status            VARCHAR(32)  NOT NULL,
    raw_request       TEXT,
    raw_response      TEXT,
    execution_time_ms BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Create partition hierarchy starting from earliest historical record
SELECT partman.create_parent(
    p_parent_table    => 'weather_bot.weather_audit_log',
    p_control         => 'created_at',
    p_interval        => '1 day',
    p_start_partition => '2026-10-14',
    p_premake         => 3,
    p_default_table   => true
);

-- Migrate existing rows into partitioned hierarchy
INSERT INTO weather_bot.weather_audit_log (
    id, correlation_id, guild_id, channel_id, user_id, command_name, trigger_type,
    query_location, resolved_location, status, raw_request, raw_response, execution_time_ms, created_at
)
OVERRIDING SYSTEM VALUE
SELECT id, correlation_id, guild_id, channel_id, user_id, command_name, trigger_type,
       query_location, resolved_location, status, raw_request, raw_response, execution_time_ms, created_at
FROM weather_bot.weather_audit_log_old;

SELECT setval(pg_get_serial_sequence('weather_bot.weather_audit_log', 'id'), COALESCE(MAX(id), 1), true)
  FROM weather_bot.weather_audit_log;

DROP TABLE weather_bot.weather_audit_log_old;

-- Local indexes across partitions
CREATE INDEX idx_weather_audit_correlation_id
    ON weather_bot.weather_audit_log (correlation_id);

CREATE INDEX idx_weather_audit_user_id
    ON weather_bot.weather_audit_log (user_id);

CREATE INDEX idx_weather_audit_created_at
    ON weather_bot.weather_audit_log (created_at DESC);

-- Configure pg_partman retention to 30 days
UPDATE partman.part_config
   SET retention = '30 days',
       retention_keep_table = false,
       retention_keep_index = false,
       infinite_time_partitions = true
 WHERE parent_table = 'weather_bot.weather_audit_log';
