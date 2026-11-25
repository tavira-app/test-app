CREATE TABLE app_metadata (
    metadata_key VARCHAR(120) PRIMARY KEY,
    metadata_value TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT app_metadata_key_nonblank CHECK (length(trim(metadata_key)) > 0)
);
