# Architecture

- Modular monolith rooted at `ir.khodroban`; domain and identity modules follow.
- PostgreSQL is the production store; Flyway owns schema changes and Hibernate
  validates the schema rather than creating or updating it.
- REST APIs will be versioned under `/api/v1`; OpenAPI documents the contract.
- Deny access by default; only health, info, and API documentation are public.
- Database and provider settings are deployment inputs; no live source data is assumed.
- Verified OIDC issuer and subject identify users; request-body identity is never trusted.
- Workspaces are accessed through explicit membership checks; owners are assigned at creation.
- Header-based identity is restricted to the opt-in `local` profile and is not an OIDC substitute.
