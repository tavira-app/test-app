CREATE TABLE listing (
    id UUID PRIMARY KEY,
    source_code VARCHAR(40) NOT NULL,
    external_id VARCHAR(200) NOT NULL,
    listing_hash VARCHAR(64) NOT NULL,
    normalized_data TEXT NOT NULL,
    normalized_hash VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT listing_source_external_unique UNIQUE(source_code, external_id),
    CONSTRAINT listing_hash_nonblank CHECK (length(trim(listing_hash)) > 0),
    CONSTRAINT listing_payload_hash_nonblank CHECK (length(trim(payload_hash)) > 0),
    CONSTRAINT listing_normalized_hash_nonblank CHECK (length(trim(normalized_hash)) > 0)
);

CREATE TABLE listing_snapshot (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    normalized_data TEXT NOT NULL,
    normalized_hash VARCHAR(64) NOT NULL,
    source_code VARCHAR(40) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT listing_snapshot_hash_unique UNIQUE(listing_id, normalized_hash),
    CONSTRAINT listing_snapshot_hash_nonblank CHECK (length(trim(normalized_hash)) > 0)
);

CREATE INDEX listing_snapshot_observed_at_idx ON listing_snapshot(observed_at DESC);

CREATE TABLE price_observation (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    price_amount INTEGER NOT NULL,
    price_currency VARCHAR(3) NOT NULL,
    price_type VARCHAR(20) NOT NULL CHECK (price_type IN ('ASKING', 'TRANSACTION')),
    source_code VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT price_observation_type_valid CHECK (price_type IN ('ASKING', 'TRANSACTION')),
    CONSTRAINT price_observation_amount_positive CHECK (price_amount > 0)
);

CREATE INDEX price_observation_listing_idx ON price_observation(listing_id, observed_at DESC);

CREATE TABLE listing_media (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    source_code VARCHAR(40) NOT NULL,
    media_url VARCHAR(500) NOT NULL,
    media_type VARCHAR(20) NOT NULL CHECK (media_type IN ('IMAGE', 'VIDEO')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT listing_media_url_nonblank CHECK (length(trim(media_url)) > 0),
    CONSTRAINT listing_media_type_valid CHECK (media_type IN ('IMAGE', 'VIDEO'))
);

CREATE INDEX listing_media_listing_idx ON listing_media(listing_id);

CREATE TABLE listing_match (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    method VARCHAR(20) NOT NULL,
    score NUMERIC(4,3) NOT NULL CHECK (score BETWEEN 0 AND 1),
    review_status VARCHAR(20) NOT NULL CHECK (review_status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT listing_match_unique_pair UNIQUE(listing_id, candidate_id),
    CONSTRAINT listing_match_method_valid CHECK (method IN ('DUPLICATE', 'REPOST', 'VARIATION'))
);

CREATE INDEX listing_match_listing_idx ON listing_match(listing_id);
CREATE INDEX listing_match_candidate_idx ON listing_match(candidate_id);
CREATE INDEX listing_match_score_idx ON listing_match(score DESC);
CREATE INDEX listing_match_reviewed_at_idx ON listing_match(reviewed_at DESC);

CREATE TABLE field_provenance (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    snapshot_id UUID REFERENCES listing_snapshot(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    raw_value TEXT,
    normalized_value TEXT,
    confidence NUMERIC(4,3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    source VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX field_provenance_listing_idx ON field_provenance(listing_id);
CREATE INDEX field_provenance_snapshot_idx ON field_provenance(snapshot_id);

CREATE TABLE data_quality_issue (
    id UUID PRIMARY KEY,
    run_id UUID REFERENCES data_ingestion_run(id),
    listing_id UUID REFERENCES listing(id),
    issue_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('WARNING', 'ERROR')),
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX data_quality_issue_run_idx ON data_quality_issue(run_id);
CREATE INDEX data_quality_issue_listing_idx ON data_quality_issue(listing_id);
CREATE INDEX data_quality_issue_resolved_idx ON data_quality_issue(resolved);

CREATE TABLE listing_source (
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    provider_id UUID NOT NULL REFERENCES source_provider(id),
    external_id VARCHAR(200) NOT NULL,
    source_metadata JSONB,
    first_received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT listing_source_provider_external_unique UNIQUE(provider_id, external_id)
);

CREATE INDEX listing_source_listing_idx ON listing_source(listing_id);
CREATE INDEX listing_source_provider_external_idx ON listing_source(provider_id, external_id);

COMMENT ON TABLE listing IS 'Canonical listing with source ID and hash for duplicate detection';
COMMENT ON TABLE listing_snapshot IS 'Historical snapshots of listing data';
COMMENT ON TABLE price_observation IS 'Price observations over time for a listing';
COMMENT ON TABLE listing_media IS 'Media references for a listing';
COMMENT ON TABLE listing_match IS 'Duplicate/repost candidate matches for listings';
COMMENT ON TABLE field_provenance IS 'Tracked provenance of normalized fields';
COMMENT ON TABLE data_quality_issue IS 'Issues detected during ingestion or processing';
COMMENT ON TABLE listing_source IS 'Link between listing and external source identifiers';
