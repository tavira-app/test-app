# Khodroban API

Java 21/Spring Boot backend. Configure `DB_URL`, `DB_USERNAME`, and
`DB_PASSWORD`; run `mvn verify` or `mvn spring-boot:run`. OpenAPI is at
`/v3/api-docs`, Swagger UI at `/swagger-ui.html`, and health at
`/actuator/health`.

Production API authentication uses OIDC JWTs. Set
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` to the trusted issuer;
the resource server validates issuer and signature using that issuer's keys.
No issuer or production credentials are embedded.

For an explicitly local-only identity smoke path, run with
`SPRING_PROFILES_ACTIVE=local` and send `X-Khodroban-User: your-local-id` to
`/api/v1/users/me`. The optional `X-Khodroban-Email` is local profile data;
never enable this profile in a public or production deployment. The test profile
uses the same filter for deterministic local identity and tests.

`/api/v1/workspaces` lists or creates the caller's workspaces. Workspace access
is membership-scoped; inaccessible workspace IDs return 404.
