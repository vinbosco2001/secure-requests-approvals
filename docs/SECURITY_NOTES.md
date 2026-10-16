# Security and publication notes

This is a **sanitized educational portfolio copy** of a project, not a production-ready service or a complete penetration-testing report.

## Removed from the original submission

- `.env` with environment values and any real secrets
- TLS private keys, signing keys, local certificates and CSRs
- PostgreSQL backups, SQL exports, archives and logs
- Full Keycloak realm export and all account/client configurations
- OWASP ZAP HTML reports, images, screenshots and documentation containing internal environment details
- Generated Maven classes and build artifacts
- Local absolute paths and machine-specific Apache/server configurations

A conservative file allowlist was used: only application source, Maven bootstrap metadata, safe example configurations and the documentation in this repo were copied.

## Development-only security compromises

`docker-compose.yml` starts Keycloak in development mode and uses an HTTP-only Vault listener. Service ports are restricted to localhost, but **this is still not secure for production**. Pin and maintain images and dependencies, set up real TLS, enforce least privilege, manage secret rotation and repeat independent security assessments before deploying.

## Before making the repository public

1. Review every file and Git diff; never rely only on `.gitignore` (it does not remove secrets already committed).
2. Check that you have permission to publish any code or material created with collaborators or university staff.
3. Rotate credentials and TLS keys if any of the original values were used outside an isolated lab or had previously been disclosed.
4. Do not include the original realm export, ZAP reports, local screenshots or personal academic documents unless they have been separately reviewed and redacted.
5. If you have accidentally pushed a secret, revoke/rotate it and clean repository history before continuing.

## Verification limits

This package was checked for excluded sensitive file categories, common credential patterns and structural consistency. It was **not** subjected to a full third-party secret scanner or a live penetration test. Application startup and integrated tests require your local Keycloak, Vault, certificate and database configuration.
