CREATE TABLE source_provider (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    configured BOOLEAN NOT NULL DEFAULT FALSE,
    access_authorized BOOLEAN NOT NULL DEFAULT FALSE,
    credential_reference VARCHAR(300),
    health_status VARCHAR(30) NOT NULL,
    health_detail VARCHAR(300) NOT NULL,
    adapter_status VARCHAR(30) NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT source_provider_type_valid CHECK (source_type IN ('SYNTHETIC','EXTERNAL')),
    CONSTRAINT source_provider_enabled_requires_configuration
        CHECK (NOT enabled OR (configured AND access_authorized))
);

CREATE TABLE data_ingestion_run (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES source_provider(id),
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    unchanged_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ingestion_run_key_unique UNIQUE(provider_id,idempotency_key),
    CONSTRAINT ingestion_run_status_valid CHECK (status IN ('RUNNING','SUCCEEDED')),
    CONSTRAINT ingestion_run_counts_nonnegative CHECK
        (created_count >= 0 AND updated_count >= 0 AND unchanged_count >= 0)
);

CREATE TABLE raw_listing (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES source_provider(id),
    external_id VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    last_run_id UUID NOT NULL REFERENCES data_ingestion_run(id),
    first_received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT raw_listing_source_id_unique UNIQUE(provider_id,external_id)
);
CREATE INDEX raw_listing_last_received_idx ON raw_listing(last_received_at DESC);

CREATE TABLE raw_listing_revision (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES source_provider(id),
    external_id VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    run_id UUID NOT NULL REFERENCES data_ingestion_run(id),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT raw_listing_revision_unique UNIQUE(provider_id,external_id,payload_hash)
);

CREATE TABLE data_ingestion_record (
    run_id UUID NOT NULL REFERENCES data_ingestion_run(id),
    provider_id UUID NOT NULL REFERENCES source_provider(id),
    external_id VARCHAR(200) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(run_id,external_id),
    CONSTRAINT ingestion_record_outcome_valid CHECK (outcome IN ('CREATED','UPDATED','UNCHANGED'))
);

INSERT INTO source_provider(id,code,display_name,source_type,enabled,configured,
    access_authorized,health_status,health_detail,adapter_status)
VALUES
    ('00000000-0000-0000-0000-000000000401','SYNTHETIC_FIXTURE',
     'Explicit synthetic test/demo fixtures','SYNTHETIC',TRUE,TRUE,TRUE,'HEALTHY',
     'Synthetic fixtures only; never live listings.','AVAILABLE'),
    ('00000000-0000-0000-0000-000000000402','DIVAR',
     'Divar','EXTERNAL',FALSE,FALSE,FALSE,'DISABLED',
     'No authorized credential reference; external adapter is not installed.','NOT_IMPLEMENTED'),
    ('00000000-0000-0000-0000-000000000403','SHEYPOOR',
     'Sheypoor','EXTERNAL',FALSE,FALSE,FALSE,'DISABLED',
     'No authorized credential reference; external adapter is not installed.','NOT_IMPLEMENTED');
