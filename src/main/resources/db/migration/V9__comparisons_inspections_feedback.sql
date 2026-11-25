CREATE TABLE vehicle_comparison (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    notes VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT vehicle_comparison_name_nonblank CHECK (length(trim(name)) > 0)
);

CREATE TABLE vehicle_comparison_item (
    comparison_id UUID NOT NULL REFERENCES vehicle_comparison(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listing(id),
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (comparison_id, listing_id),
    UNIQUE (comparison_id, position)
);

CREATE TABLE inspection_checklist (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    items_json TEXT NOT NULL,
    provenance_source VARCHAR(120) NOT NULL,
    author_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inspection_checklist_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT inspection_checklist_items_nonblank CHECK (length(trim(items_json)) > 0)
);

CREATE TABLE inspection_report (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listing(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    report_json TEXT NOT NULL,
    provenance_source VARCHAR(120) NOT NULL,
    confidence NUMERIC(4,3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    author_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inspection_report_json_nonblank CHECK (length(trim(report_json)) > 0)
);

CREATE TABLE user_feedback (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    target_type VARCHAR(40) NOT NULL CHECK (target_type IN ('LISTING','COMPARISON','INSPECTION_REPORT')),
    target_id UUID NOT NULL,
    category VARCHAR(40) NOT NULL,
    comment VARCHAR(4000),
    rating INTEGER CHECK (rating BETWEEN 1 AND 5),
    author_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT feedback_content_present CHECK (comment IS NOT NULL OR rating IS NOT NULL)
);

CREATE INDEX vehicle_comparison_owner_idx ON vehicle_comparison(user_id, created_at DESC);
CREATE INDEX vehicle_comparison_item_listing_idx ON vehicle_comparison_item(listing_id);
CREATE INDEX inspection_checklist_owner_idx ON inspection_checklist(user_id, created_at DESC);
CREATE INDEX inspection_report_listing_owner_idx ON inspection_report(listing_id, user_id, created_at DESC);
CREATE INDEX user_feedback_owner_idx ON user_feedback(user_id, created_at DESC);
