# Local development setup

These notes describe the architecture of the **original academic lab**. They are not a production installation guide, and some manual provisioning is required.

## Prerequisites

- Java 17+ and Maven 3.x (or `./mvnw` on Linux/macOS; `mvnw.cmd` on Windows)
- Docker and Docker Compose
- A local reverse proxy with Apache HTTP Server, mod_ssl and mod_proxy enabled
- Two locally resolvable development names: `requests.local` and `auth.local`
- Development-only TLS certificates under your control (never commit the private keys)

## 1. Start local dependencies

```sh
cp .env.example .env
# Edit .env; replace every placeholder password with a strong, unique local password.
docker compose up -d
```

The Compose stack starts PostgreSQL, Keycloak and Vault. Exposed service ports are bound to `127.0.0.1` for the host's local environment. It deliberately uses **Keycloak `start-dev`** and a **Vault HTTP listener**, neither of which is suitable for production.

This Compose file **does not** start the Spring application or Apache. Keycloak shares the PostgreSQL service with the application in the original lab configuration.

## 2. Set up Vault

Vault uses persistent file storage, so a new instance must be **initialized and unsealed**. Protect unseal keys and the initial admin token outside this repository. Enable a KV-v2 secrets engine mounted at `secret/` if it is not already enabled.

In that engine, create the application configuration record at `secret/requests-app` with the following keys:

```text
db_username           = <your local PostgreSQL user>
db_password           = <your local PostgreSQL password>
keycloak_client_secret = <the secret for Keycloak client requests-app>
```

At launch, the Spring application reads its Vault token from the `VAULT_TOKEN` environment variable (not from a committed file):

```sh
export VAULT_TOKEN="<your local Vault token>"
```

Make sure the Vault token is appropriately scoped to read the application's secrets. The placeholder above is not a real token. Never paste production tokens into documentation, issue trackers or screenshots.

## 3. Configure Keycloak

1. Create the realm `requests-approvals`.
2. Create a confidential OIDC client `requests-app`, with the Authorization Code flow enabled and redirect URI under `https://requests.local/login/oauth2/code/keycloak`.
3. Configure two roles, `USER` and `ADMIN`, and create **test-only accounts**.
4. Enable the authorization services for the client; configure matching resources and scopes for the policies referenced by the application's `@PreAuthorize` methods.
5. Ensure Keycloak is available at `https://auth.local` through your local TLS reverse proxy.
6. Copy the OIDC client secret into your local Vault record. The **original exported realm JSON has been excluded** because it can contain users, credential hashes and client secrets.

Resources/scopes referenced by the application source:

| Resource | Required scopes |
|---|---|
| `requests-user-area` | `view` |
| `request` | `create`, `approve`, `reject` |
| `requests-profile` | `view`, `password-change`, `email-change`, `delete-account` |
| `requests-admin-area` | `view` |

Grant user capabilities to the user role and administrative capabilities to the administrator role. Refer to the controller source for the exact access checks. Configure the Keycloak Admin API permissions required by the profile and account-management functions.

## 4. Configure local HTTPS

The application expects `https://requests.local` and Keycloak's issuer to be `https://auth.local/realms/requests-approvals`. Configure local host resolution and an Apache HTTPS reverse proxy to forward the application to `http://127.0.0.1:8081` and Keycloak to `http://127.0.0.1:8083`.

The original Apache configuration included absolute machine-specific paths and local TLS private-key references, so it was intentionally **not** copied into this repository. Create a new deployment-local configuration instead. In production, also protect internal links, use hardened non-development services and manage certificates with trusted infrastructure.

## 5. Start the Java application

With Vault, the realm and the reverse proxy configured:

```sh
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run`, or Maven's `mvn spring-boot:run` where installed. The application connects to PostgreSQL on `localhost:5432` and Vault on `localhost:8200` in the original development configuration.

## Limitations

- No ready-to-import production Keycloak realm or TLS key material is supplied.
- The included test is an original Spring context-loading test; it is not a full automated integration/security test suite.
- The setup instructions document the original development architecture; starting the application depends on matching local provisioning.
- Do not deploy the included local Compose environment onto a publicly accessible server.
