CREATE TABLE vehicle_make (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    name_normalized VARCHAR(120) NOT NULL UNIQUE,
    country_code VARCHAR(2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT vehicle_make_name_nonblank CHECK (length(trim(name)) > 0)
);

CREATE TABLE vehicle_model (
    id UUID PRIMARY KEY,
    make_id UUID NOT NULL REFERENCES vehicle_make(id),
    name VARCHAR(120) NOT NULL,
    name_normalized VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT vehicle_model_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT vehicle_model_name_unique UNIQUE (make_id, name_normalized)
);
CREATE INDEX vehicle_model_make_idx ON vehicle_model(make_id, name_normalized);

CREATE TABLE vehicle_generation (
    id UUID PRIMARY KEY,
    model_id UUID NOT NULL REFERENCES vehicle_model(id),
    name VARCHAR(120) NOT NULL,
    name_normalized VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT vehicle_generation_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT vehicle_generation_name_unique UNIQUE (model_id, name_normalized),
    CONSTRAINT vehicle_generation_id_model_unique UNIQUE (id, model_id)
);

CREATE TABLE vehicle_trim (
    id UUID PRIMARY KEY,
    model_id UUID NOT NULL REFERENCES vehicle_model(id),
    generation_id UUID,
    name VARCHAR(120) NOT NULL,
    name_normalized VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT vehicle_trim_generation_model_fk FOREIGN KEY (generation_id, model_id)
        REFERENCES vehicle_generation(id, model_id),
    CONSTRAINT vehicle_trim_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT vehicle_trim_name_unique UNIQUE (model_id, name_normalized)
);
CREATE INDEX vehicle_trim_model_idx ON vehicle_trim(model_id, name_normalized);

CREATE TABLE vehicle_trim_alias (
    id UUID PRIMARY KEY,
    trim_id UUID NOT NULL REFERENCES vehicle_trim(id) ON DELETE CASCADE,
    alias VARCHAR(120) NOT NULL,
    alias_normalized VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT vehicle_trim_alias_nonblank CHECK (length(trim(alias)) > 0),
    CONSTRAINT vehicle_trim_alias_unique UNIQUE (trim_id, alias_normalized)
);
CREATE INDEX vehicle_trim_alias_normalized_idx ON vehicle_trim_alias(alias_normalized);

CREATE TABLE vehicle_specification (
    trim_id UUID PRIMARY KEY REFERENCES vehicle_trim(id) ON DELETE CASCADE,
    engine_cc INTEGER CHECK (engine_cc >= 0),
    power_kw NUMERIC(8,2) CHECK (power_kw >= 0),
    fuel_type VARCHAR(60),
    transmission VARCHAR(60),
    consumption_litres_per_100_km NUMERIC(6,2) CHECK (consumption_litres_per_100_km >= 0),
    provenance_source VARCHAR(120) NOT NULL,
    confidence NUMERIC(4,3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
