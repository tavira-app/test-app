CREATE TABLE comparable_set (
    id UUID PRIMARY KEY,
    criteria_json TEXT NOT NULL,
    sample_count INTEGER NOT NULL CHECK (sample_count >= 0),
    sample_listing_ids TEXT NOT NULL,
    selected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_type VARCHAR(20) NOT NULL CHECK (data_type IN ('ASKING', 'TRANSACTION'))
);

CREATE TABLE price_estimate (
    id UUID PRIMARY KEY,
    comparable_set_id UUID NOT NULL REFERENCES comparable_set(id),
    listing_id UUID NOT NULL REFERENCES listing(id),
    median_amount NUMERIC(20,2),
    p25_amount NUMERIC(20,2),
    p75_amount NUMERIC(20,2),
    delta_amount NUMERIC(20,2),
    delta_percent NUMERIC(12,2),
    sample_count INTEGER NOT NULL CHECK (sample_count >= 0),
    confidence NUMERIC(4,3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    status VARCHAR(30) NOT NULL CHECK (status IN ('AVAILABLE', 'INSUFFICIENT_DATA')),
    data_type VARCHAR(20) NOT NULL CHECK (data_type IN ('ASKING', 'TRANSACTION')),
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX comparable_set_selected_idx ON comparable_set(selected_at DESC);
CREATE INDEX price_estimate_listing_idx ON price_estimate(listing_id, calculated_at DESC);
CREATE INDEX price_estimate_status_idx ON price_estimate(status, calculated_at DESC);

COMMENT ON TABLE comparable_set IS 'Persisted structured comparable selection; prices are explicitly typed.';
COMMENT ON TABLE price_estimate IS 'Persisted asking-price estimate, never a verified transaction price.';
