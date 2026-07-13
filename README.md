# Byzantine Directory Service

Org/user profiles, tenants (groups), membership, and invites. Orgs are created in IAM; this service stores profiles and membership only.

- Local: `mvn spring-boot:run -Dspring-boot.run.profiles=local` (port **8086**, DB on **5435**)
- DB: `docker compose up -d byz-directory-db` from `projects/db`
- Deployed: `https://directory.byzantineapp.dev`
- Auth: IAM JWT via JWKS (`organization_id` claim required for most routes)
