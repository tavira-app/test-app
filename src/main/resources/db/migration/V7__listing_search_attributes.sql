ALTER TABLE listing ADD COLUMN region VARCHAR(120);
ALTER TABLE listing ADD COLUMN fuel_type VARCHAR(60);
ALTER TABLE listing ADD COLUMN transmission VARCHAR(60);
ALTER TABLE listing ADD COLUMN body_type VARCHAR(60);

CREATE INDEX listing_search_year_mileage_idx
    ON listing(model_year, mileage_km);
CREATE INDEX listing_search_price_idx
    ON listing(asking_price_amount);
CREATE INDEX listing_search_region_idx
    ON listing(region);
CREATE INDEX listing_search_attributes_idx
    ON listing(fuel_type, transmission, body_type);
