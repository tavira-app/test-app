# Vehicle catalog API

Catalog read and search endpoints are under `/api/v1/catalog` and require
authentication under the existing API security policy. Make, model, generation,
trim, alias, and specification writes require `OPS_ADMIN`.

The additive Flyway migration stores normalized catalog names, parent
relationships, technical specifications, and specification provenance and
confidence. `/resolve` accepts an exact normalized trim name or alias; a
missing or ambiguous match is returned as `UNKNOWN` with zero confidence rather
than guessed. Catalog contents are not seeded with purported live vehicle data.

`CatalogPostgresIntegrationTest` starts PostgreSQL 16 with Testcontainers and
exercises Flyway migrations, persistence, API reads/search, validation, and
resolution against that database. Docker must be available when running
`mvn verify`; container startup failures fail the test rather than skipping
PostgreSQL verification. The test uses synthetic catalog fixtures only.
