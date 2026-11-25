CREATE TABLE favorite (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, listing_id)
);
CREATE INDEX favorite_workspace_idx ON favorite(workspace_id, created_at DESC);

CREATE TABLE saved_search (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    criteria TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    new_listing BOOLEAN NOT NULL DEFAULT TRUE,
    price_change BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX saved_search_owner_idx ON saved_search(user_id, active, created_at DESC);

CREATE TABLE alert_preference (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    consent BOOLEAN NOT NULL DEFAULT FALSE,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    daily_quota INTEGER NOT NULL DEFAULT 0 CHECK (daily_quota >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, workspace_id),
    CONSTRAINT alert_preference_channel_valid CHECK (channel IN ('IN_APP'))
);

CREATE TABLE alert_event (
    id UUID PRIMARY KEY,
    saved_search_id UUID NOT NULL REFERENCES saved_search(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('NEW_LISTING', 'PRICE_CHANGE')),
    event_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(saved_search_id, listing_id, event_type, event_key)
);

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE REFERENCES alert_event(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    saved_search_id UUID NOT NULL REFERENCES saved_search(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notification_outbox_status_valid CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);
CREATE INDEX notification_outbox_user_idx ON notification_outbox(user_id, created_at DESC);
CREATE INDEX notification_outbox_delivery_idx ON notification_outbox(status, next_attempt_at);

CREATE TABLE job_record (
    id UUID PRIMARY KEY,
    job_name VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    generated_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT job_record_status_valid CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE TABLE alert_job_cursor (
    job_name VARCHAR(80) PRIMARY KEY,
    cursor_at TIMESTAMP WITH TIME ZONE NOT NULL
);
INSERT INTO alert_job_cursor(job_name, cursor_at)
VALUES ('listing-alerts', CURRENT_TIMESTAMP);
