CREATE SCHEMA IF NOT EXISTS weather_bot;

CREATE TABLE IF NOT EXISTS weather_bot.user_weather_config (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL,
    city VARCHAR(128) NOT NULL,
    state VARCHAR(64),
    country VARCHAR(64),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_weather_config_user_id UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_weather_config_user_id ON weather_bot.user_weather_config (user_id);

CREATE TABLE IF NOT EXISTS weather_bot.weather_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    guild_id VARCHAR(32),
    channel_id VARCHAR(32),
    user_id VARCHAR(32) NOT NULL,
    origin_message_id VARCHAR(32),
    location_name VARCHAR(256) NOT NULL,
    location_state VARCHAR(64),
    location_country VARCHAR(64),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    current_day_index INT NOT NULL DEFAULT 0,
    forecast_json TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_weather_sessions_session_id UNIQUE (session_id)
);

CREATE INDEX IF NOT EXISTS idx_weather_sessions_session_id ON weather_bot.weather_sessions (session_id);
CREATE INDEX IF NOT EXISTS idx_weather_sessions_expires_at ON weather_bot.weather_sessions (expires_at);

CREATE TABLE IF NOT EXISTS weather_bot.weather_audit_log (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(64) NOT NULL,
    guild_id VARCHAR(32),
    channel_id VARCHAR(32),
    user_id VARCHAR(32) NOT NULL,
    command_name VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(16) NOT NULL,
    query_location VARCHAR(256),
    resolved_location VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    raw_request TEXT,
    raw_response TEXT,
    execution_time_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_weather_audit_correlation_id ON weather_bot.weather_audit_log (correlation_id);
CREATE INDEX IF NOT EXISTS idx_weather_audit_user_id ON weather_bot.weather_audit_log (user_id);
CREATE INDEX IF NOT EXISTS idx_weather_audit_created_at ON weather_bot.weather_audit_log (created_at DESC);
