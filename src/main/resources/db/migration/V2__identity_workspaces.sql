CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    issuer_subject VARCHAR(500) NOT NULL UNIQUE,
    email VARCHAR(320),
    display_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT app_user_subject_nonblank CHECK (length(trim(issuer_subject)) > 0),
    CONSTRAINT app_user_name_nonblank CHECK (length(trim(display_name)) > 0)
);

CREATE TABLE workspace (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workspace_name_nonblank CHECK (length(trim(name)) > 0)
);

CREATE TABLE workspace_member (
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, user_id),
    CONSTRAINT workspace_member_role_valid CHECK (role IN ('OWNER', 'MEMBER', 'VIEWER'))
);

CREATE INDEX workspace_member_user_idx ON workspace_member(user_id, workspace_id);
CREATE INDEX workspace_created_by_idx ON workspace(created_by);
