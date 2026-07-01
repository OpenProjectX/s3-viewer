# S3 Viewer

Multi-cloud S3 browser built with Spring Boot WebFlux, Kotlin, OpenAPI-generated controllers, and a React + MUI frontend.

## Modules

| Module | Purpose |
|---|---|
| `app` | Standalone Spring Boot demo application |
| `autoconfigure` | Auto-configuration: service, REST API, UI static assets |
| `core` | Shared domain interfaces and types |
| `starter` | Spring Boot starter — add this one dependency to get everything |
| `ui` | Vite + React + TypeScript + MUI frontend |

Add the starter to your project and you get the full UI + REST API out of the box:
- UI served at `/s3-viewer/ui/`
- API served at `/s3-viewer/api/v1/`

## REST API

All endpoints are under `/s3-viewer/api/v1`. The OpenAPI spec is at `autoconfigure/src/main/resources/openapi/api.yaml` and controllers are generated via the OpenAPI Generator Gradle plugin — **do not edit generated sources directly**.

### Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/s3-viewer/api/v1/providers` | List configured S3 providers |
| `GET` | `/s3-viewer/api/v1/providers/{id}/buckets` | List buckets for a provider |
| `GET` | `/s3-viewer/api/v1/providers/{id}/buckets/{name}/browse` | Browse objects at a path (`?path=prefix`) |
| `GET` | `/s3-viewer/api/v1/providers/{id}/buckets/{name}/download` | Download an object (`?key=object/key`) |
| `GET` | `/s3-viewer/api/v1/providers/{id}/buckets/{name}/preview/text` | Preview supported text objects (`?key=object/key&maxBytes=1048576`) |
| `GET` | `/s3-viewer/api/v1/providers/{id}/buckets/{name}/preview/parquet/schema` | Preview parquet schema only (`?key=object/key`) |
| `POST` | `/s3-viewer/api/v1/providers/{id}/buckets/{name}/folders` | Create a virtual folder (JSON body: `{"path": "prefix", "folderName": "name"}`) |
| `POST` | `/s3-viewer/api/v1/providers/{id}/buckets/{name}/upload` | Upload a file (`multipart/form-data`, `?path=prefix`) |
| `DELETE` | `/s3-viewer/api/v1/providers/{id}/buckets/{name}/objects` | Delete objects (JSON body: `{"keys": [...]}`) |
| `GET` | `/s3-viewer/api/v1/providers/{id}/buckets/{name}/search` | Search objects by name (`?query=term&path=prefix&maxResults=100`) |

### Breaking changes from 0.1.x

- `S3ViewerService` interface has new object operation methods: `downloadObject`, `previewTextObject`, `previewParquetSchema`, `uploadObject`, `deleteObjects`, `searchObjects`. Any custom implementation must implement these.
- `ObjectDownload`, `TextObjectPreview`, `ParquetSchemaPreview`, and `SearchResult` are domain types added to the `core` module.

## Configuration

Provider configuration lives under `s3-viewer.providers` in `application.yaml`:

```yaml
s3-viewer:
  read-only-access: false
  providers:
    - id: my-provider
      name: My S3
      endpoint: https://s3.amazonaws.com
      region: us-east-1
      access-key: AKIAIOSFODNN7EXAMPLE
      secret-key: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
      path-style-access: false
      buckets:
        - my-bucket
        - another-bucket
```

Use `all-buckets: true` to list and allow every bucket visible to the provider credentials instead of maintaining an explicit bucket allow-list:

```yaml
s3-viewer:
  providers:
    - id: my-provider
      name: My S3
      endpoint: https://s3.amazonaws.com
      region: us-east-1
      access-key: AKIAIOSFODNN7EXAMPLE
      secret-key: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
      path-style-access: false
      all-buckets: true
```

For LocalStack (dev profile), see `app/src/main/resources/application-dev.yaml`.

Set `s3-viewer.read-only-access=true` to reject all write operations. Upload and delete requests return `403`, and the bundled UI hides upload, delete, and multi-select controls.

### Basic LDAP security and RBAC

Security is disabled by default. Enable HTTP Basic authentication backed by LDAP with:

```yaml
spring:
  ldap:
    urls: ldap://ad.example.com:389
    base: DC=example,DC=com
    username: CN=s3-viewer-bind,OU=Service Accounts,DC=example,DC=com
    password: ${LDAP_MANAGER_PASSWORD}

s3-viewer:
  security:
    enabled: true
    ldap:
      authentication-mode: search
      user-search-base: OU=Users
      user-search-filter: "(&(objectClass=user)(sAMAccountName={0}))"
      member-of-attribute: memberOf
```

LDAP connection, base DN, and optional bind credentials use Spring Boot's standard `spring.ldap.*` configuration. S3 Viewer configures authentication behavior and application role mapping.

After a successful LDAP Basic authentication, S3 Viewer stores the Spring Security context in the WebFlux web session. Clients can reuse the returned `SESSION` cookie for later API calls instead of sending the Basic authorization header on every request.

Use these Spring Boot LDAP properties for directory connectivity:

| Property | Purpose |
| --- | --- |
| `spring.ldap.urls` | LDAP server URL or URLs, for example `ldap://ad.example.com:389` |
| `spring.ldap.base` | Directory base DN used by LDAP searches and relative direct-bind DN patterns |
| `spring.ldap.username` | Bind DN for authenticated LDAP searches; not required for direct-bind mode |
| `spring.ldap.password` | Bind password; not required for direct-bind mode |

Use these S3 Viewer LDAP properties for authentication and role extraction:

| Property | Default | Purpose |
| --- | --- | --- |
| `s3-viewer.security.ldap.authentication-mode` | `search` | LDAP authentication mode. Use `search` for manager-bind search then user bind, or `direct-bind` to bind directly with user DN patterns |
| `s3-viewer.security.ldap.user-search-base` | empty | Search base relative to `spring.ldap.base` |
| `s3-viewer.security.ldap.user-search-filter` | `(&(objectClass=user)(sAMAccountName={0}))` | LDAP user search filter; `{0}` is the Basic auth username |
| `s3-viewer.security.ldap.user-dn-patterns` | empty | DN patterns used by `direct-bind`; `{0}` is the Basic auth username. Patterns may be relative to `spring.ldap.base` |
| `s3-viewer.security.ldap.member-of-attribute` | `memberOf` | Attribute used to derive roles from Microsoft Active Directory groups |
| `s3-viewer.security.ldap.role-prefix` | `ROLE_` | Prefix applied to generated Spring Security authorities |
| `s3-viewer.security.ldap.role-mappings` | empty | Explicit mapping from application roles to AD group CNs or DNs |

The old S3 Viewer connection properties `s3-viewer.security.ldap.url`, `base-dn`, `manager-dn`, and `manager-password` are no longer used; configure those values with `spring.ldap.*`.

Use direct-bind mode when the client username can be formatted into the user's LDAP DN and you do not want to configure a manager bind DN:

```yaml
spring:
  ldap:
    urls: ldap://ad.example.com:389
    base: DC=example,DC=com

s3-viewer:
  security:
    enabled: true
    ldap:
      authentication-mode: direct-bind
      user-dn-patterns:
        - "CN={0},OU=Users"
      member-of-attribute: memberOf
```

With this mode the application binds as the supplied user DN and password directly. `memberOf` RBAC still requires the directory to return the configured membership attribute for the bound user.

By default, roles are derived from Microsoft Active Directory `memberOf` values. For a group DN like `CN=S3 Viewer Admins,OU=Groups,DC=example,DC=com`, the user gets authority `ROLE_S3_VIEWER_ADMINS`.

RBAC is configurable under `s3-viewer.security.rbac.rules`. Rules are evaluated before the default authenticated rule for `/s3-viewer/**`. Role names may be written with or without `ROLE_`.

```yaml
s3-viewer:
  security:
    rbac:
      enabled: true
      rules:
        - path: /s3-viewer/api/v1/providers/{providerId}/buckets/{bucketName}/objects
          methods: [DELETE]
          roles: [S3_VIEWER_ADMIN]
        - path: /s3-viewer/api/v1/providers/{providerId}/buckets/{bucketName}/upload
          methods: [POST]
          roles: [S3_VIEWER_ADMIN]
```

The built-in RBAC defaults require `ROLE_S3_VIEWER_ADMIN` or `ROLE_ADMIN` for upload, folder creation, and delete. Other S3 Viewer routes require any authenticated LDAP user.

Use `role-mappings` when the AD group CN should not be the application role name:

```yaml
s3-viewer:
  security:
    ldap:
      role-mappings:
        S3_VIEWER_ADMIN:
          - CN=Data Platform Admins,OU=Groups,DC=example,DC=com
```

The same configuration can be supplied with environment variables:

```bash
export SPRING_LDAP_URLS=ldap://ad.example.com:389
export SPRING_LDAP_BASE='DC=example,DC=com'
export SPRING_LDAP_USERNAME='CN=s3-viewer-bind,OU=Service Accounts,DC=example,DC=com'
export SPRING_LDAP_PASSWORD="$LDAP_MANAGER_PASSWORD"
export S3_VIEWER_SECURITY_ENABLED=true
export S3_VIEWER_SECURITY_LDAP_AUTHENTICATION_MODE=search
export S3_VIEWER_SECURITY_LDAP_USER_SEARCH_BASE='OU=Users'
export S3_VIEWER_SECURITY_LDAP_USER_SEARCH_FILTER='(&(objectClass=user)(sAMAccountName={0}))'
export S3_VIEWER_SECURITY_LDAP_ROLE_MAPPINGS_S3_VIEWER_ADMIN_0='CN=Data Platform Admins,OU=Groups,DC=example,DC=com'
```

Provider configuration can also be supplied entirely through environment variables. Spring Boot maps indexed list properties like `s3-viewer.providers[0].id` to env vars using uppercase names and underscores:

```bash
export S3_VIEWER_READ_ONLY_ACCESS=true
export S3_VIEWER_PROVIDERS_0_ID=aws
export S3_VIEWER_PROVIDERS_0_NAME="AWS S3"
export S3_VIEWER_PROVIDERS_0_ENDPOINT=https://s3.amazonaws.com
export S3_VIEWER_PROVIDERS_0_REGION=us-east-1
export S3_VIEWER_PROVIDERS_0_ACCESS_KEY="$AWS_ACCESS_KEY_ID"
export S3_VIEWER_PROVIDERS_0_SECRET_KEY="$AWS_SECRET_ACCESS_KEY"
export S3_VIEWER_PROVIDERS_0_PATH_STYLE_ACCESS=false
export S3_VIEWER_PROVIDERS_0_BUCKETS_0=my-bucket
export S3_VIEWER_PROVIDERS_0_BUCKETS_1=another-bucket
```

For multiple providers, increment the provider index:

```bash
export S3_VIEWER_PROVIDERS_1_ID=minio
export S3_VIEWER_PROVIDERS_1_NAME=MinIO
export S3_VIEWER_PROVIDERS_1_ENDPOINT=http://minio:9000
export S3_VIEWER_PROVIDERS_1_REGION=us-east-1
export S3_VIEWER_PROVIDERS_1_ACCESS_KEY=minioadmin
export S3_VIEWER_PROVIDERS_1_SECRET_KEY=minioadmin
export S3_VIEWER_PROVIDERS_1_PATH_STYLE_ACCESS=true
export S3_VIEWER_PROVIDERS_1_BUCKETS_0=demo
```

### Reverse proxy / context path

When the application sits behind a reverse proxy that **does not strip** its path prefix, tell the UI where the API lives:

```yaml
s3-viewer:
  ui:
    api-base-path: /prod/service/s3-viewer/api   # default: /s3-viewer/api
```

Spring Boot serves `GET /s3-viewer/ui/config.js` which injects this value into the browser as `window.__S3_VIEWER_CONFIG__.apiBase` before the React bundle runs. The UI assets themselves use relative paths in the build output so they work under any prefix without configuration.

## UI

The `ui` module is a React + Vite + MUI single-page application. Features:

- Sidebar with provider/bucket navigation tree
- File browser with **list** and **grid** view modes
- Breadcrumb path navigation
- File-type icons (images, video, audio, code, archives, data files, etc.)
- **Search** — live case-insensitive name search within a bucket/path
- **Preview** — inline review for `.txt` and `.json` content, plus parquet schema without reading row data
- **Create folder** — create virtual folders under the current bucket path
- **Upload** — drag & drop or file picker with per-file progress bars
- **Download** — direct download button per file
- **Delete** — multi-select with confirmation dialog

Preview content type is read from S3 object metadata first. Because S3 `Content-Type` is user-supplied and can be missing or wrong, the backend falls back to JVM content probing and filename extensions for supported preview types.

When `s3-viewer.read-only-access=true`, write controls are hidden and the backend still rejects upload/delete requests with `403`.

In dev mode the Vite server proxies `/s3-viewer/api` requests to the Spring app on port `8081`.

## Building & running

```bash
# Build a self-contained JAR (builds UI, copies dist into static/)
./gradlew :app:bootJar

./gradlew release -Prelease.useAutomaticVersion=true
```

## Container Image

Release builds publish the standalone app image to GitHub Container Registry:

```bash
docker pull ghcr.io/openprojectx/s3-viewer:<version>
```

Run with the default application config:

```bash
docker run --rm -p 8081:8081 ghcr.io/openprojectx/s3-viewer:<version>
```

The UI is available at `http://localhost:8081/s3-viewer/ui/`.

For a local multi-provider playground, use the Compose example:

```bash
docker compose -f docker-compose.example.yml up
```

It starts S3 Viewer from `ghcr.io/openprojectx/s3-viewer:latest` and configures three local AWS/S3-compatible providers:

| Provider | Image | Host endpoint |
|---|---|---|
| LocalStack | `ghcr.io/openprojectx/dockerhub/localstack/localstack:4` | `http://localhost:4566` |
| MiniStack | `ministackorg/ministack` | `http://localhost:4567` |
| Floci | `floci/floci:latest` | `http://localhost:4568` |

Inside the Compose network, S3 Viewer talks to each provider on its service DNS name and port `4566`.

The example config uses buckets named `demo-assets` and `demo-archive`. LocalStack creates them through the existing init script. For MiniStack and Floci, create them with the AWS CLI after startup if you want all three providers immediately browsable:

```bash
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4567 s3 mb s3://demo-assets
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4567 s3 mb s3://demo-archive

AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4568 s3 mb s3://demo-assets
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4568 s3 mb s3://demo-archive
```

For real S3 providers, mount a Spring Boot config file into `/config/application.yaml`:

```bash
docker run --rm -p 8081:8081 \
  -v "$PWD/application.yaml:/config/application.yaml:ro" \
  ghcr.io/openprojectx/s3-viewer:<version>
```

Example `application.yaml`:

```yaml
s3-viewer:
  read-only-access: true
  providers:
    - id: aws
      name: AWS S3
      endpoint: https://s3.amazonaws.com
      region: us-east-1
      access-key: ${AWS_ACCESS_KEY_ID}
      secret-key: ${AWS_SECRET_ACCESS_KEY}
      path-style-access: false
      all-buckets: true
```

Pass credentials with environment variables:

```bash
docker run --rm -p 8081:8081 \
  -e AWS_ACCESS_KEY_ID=... \
  -e AWS_SECRET_ACCESS_KEY=... \
  -v "$PWD/application.yaml:/config/application.yaml:ro" \
  ghcr.io/openprojectx/s3-viewer:<version>
```

You can also configure providers directly with container environment variables and skip the mounted YAML file:

```bash
docker run --rm -p 8081:8081 \
  -e S3_VIEWER_READ_ONLY_ACCESS=true \
  -e S3_VIEWER_PROVIDERS_0_ID=aws \
  -e S3_VIEWER_PROVIDERS_0_NAME="AWS S3" \
  -e S3_VIEWER_PROVIDERS_0_ENDPOINT=https://s3.amazonaws.com \
  -e S3_VIEWER_PROVIDERS_0_REGION=us-east-1 \
  -e S3_VIEWER_PROVIDERS_0_ACCESS_KEY=... \
  -e S3_VIEWER_PROVIDERS_0_SECRET_KEY=... \
  -e S3_VIEWER_PROVIDERS_0_PATH_STYLE_ACCESS=false \
  -e S3_VIEWER_PROVIDERS_0_BUCKETS_0=my-bucket \
  ghcr.io/openprojectx/s3-viewer:<version>
```

Local image builds use Jib and do not require a Dockerfile:

```bash
# Build into the local Docker daemon
./gradlew :app:jibDockerBuild

# Push to a registry
JIB_TO_IMAGE=ghcr.io/openprojectx/s3-viewer ./gradlew :app:jib
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full dev setup, Dev Container instructions, and how to run the app locally.
