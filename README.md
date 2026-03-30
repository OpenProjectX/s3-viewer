# S3 Viewer

Multi-cloud S3 browser built with Spring Boot WebFlux, Kotlin, OpenAPI-generated controllers, and a React + MUI frontend.

## Modules

| Module | Purpose |
|---|---|
| `app` | Spring Boot application, OpenAPI contract, REST controllers |
| `autoconfigure` | Configuration properties and `DefaultS3ViewerService` |
| `core` | Shared domain interfaces and types |
| `starter` | Spring Boot starter for embedding the service |
| `ui` | Vite + React + TypeScript + MUI frontend |

## REST API

All endpoints are under `/api/v1`. The OpenAPI spec is at `app/src/main/resources/openapi/api.yaml` and controllers are generated via the OpenAPI Generator Gradle plugin — **do not edit generated sources directly**.

### Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/providers` | List configured S3 providers |
| `GET` | `/providers/{id}/buckets` | List buckets for a provider |
| `GET` | `/providers/{id}/buckets/{name}/browse` | Browse objects at a path (`?path=prefix`) |
| `GET` | `/providers/{id}/buckets/{name}/download` | Download an object (`?key=object/key`) |
| `POST` | `/providers/{id}/buckets/{name}/upload` | Upload a file (`multipart/form-data`, `?path=prefix`) |
| `DELETE` | `/providers/{id}/buckets/{name}/objects` | Delete objects (JSON body: `{"keys": [...]}`) |
| `GET` | `/providers/{id}/buckets/{name}/search` | Search objects by name (`?query=term&path=prefix&maxResults=100`) |

### Breaking changes from 0.1.x

- `S3ViewerService` interface has **four new methods**: `downloadObject`, `uploadObject`, `deleteObjects`, `searchObjects`. Any custom implementation must implement these.
- `ObjectDownload` and `SearchResult` are new domain types added to the `core` module.

## Configuration

Provider configuration lives under `s3-viewer.providers` in `application.yaml`:

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
      buckets:
        - my-bucket
        - another-bucket
```

For LocalStack (dev profile), see `app/src/main/resources/application-dev.yaml`.

## UI

The `ui` module is a React + Vite + MUI single-page application. Features:

- Sidebar with provider/bucket navigation tree
- File browser with **list** and **grid** view modes
- Breadcrumb path navigation
- File-type icons (images, video, audio, code, archives, data files, etc.)
- **Search** — live case-insensitive name search within a bucket/path
- **Upload** — drag & drop or file picker with per-file progress bars
- **Download** — direct download button per file
- **Delete** — multi-select with confirmation dialog

In dev mode the Vite server proxies `/api` requests to the Spring app on port `8081`.

## Building & running

```bash
# Build a self-contained JAR (builds UI, copies dist into static/)
./gradlew :app:bootJar

./gradlew release -Prelease.useAutomaticVersion=true
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full dev setup, Dev Container instructions, and how to run the app locally.
