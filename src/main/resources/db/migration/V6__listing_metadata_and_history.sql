ALTER TABLE listing
    ADD COLUMN listing_title VARCHAR(500);
ALTER TABLE listing ADD COLUMN normalized_make VARCHAR(120);
ALTER TABLE listing ADD COLUMN normalized_model VARCHAR(120);
ALTER TABLE listing ADD COLUMN normalized_trim VARCHAR(120);
ALTER TABLE listing ADD COLUMN model_year INTEGER
    CHECK (model_year BETWEEN 1886 AND 2200);
ALTER TABLE listing ADD COLUMN mileage_km NUMERIC(14,2)
    CHECK (mileage_km >= 0);
ALTER TABLE listing ADD COLUMN asking_price_amount BIGINT
    CHECK (asking_price_amount > 0);
ALTER TABLE listing ADD COLUMN asking_price_currency VARCHAR(3);
ALTER TABLE listing ADD COLUMN city VARCHAR(120);
ALTER TABLE listing ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE listing ADD COLUMN source_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE listing ADD COLUMN normalization_confidence NUMERIC(4,3)
    NOT NULL DEFAULT 0 CHECK (normalization_confidence BETWEEN 0 AND 1);

ALTER TABLE price_observation
    ALTER COLUMN price_amount TYPE BIGINT;
ALTER TABLE price_observation
    ADD CONSTRAINT price_observation_currency_valid
    CHECK (price_currency ~ '^[A-Z]{3}$');

ALTER TABLE listing_media
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE listing_media
    ADD COLUMN provenance_source VARCHAR(120) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE listing_media
    ADD COLUMN confidence NUMERIC(4,3) NOT NULL DEFAULT 0
        CHECK (confidence BETWEEN 0 AND 1);

CREATE UNIQUE INDEX listing_media_listing_url_unique ON listing_media(listing_id, media_url);
CREATE INDEX listing_normalized_vehicle_idx ON listing(normalized_make, normalized_model, model_year);
CREATE INDEX listing_city_price_idx ON listing(city, asking_price_amount);
