# Contributing

## Prerequisites

- JDK 17+
- Node 20+ with Yarn
- Docker (for LocalStack via Dev Container or standalone)

## Setting up the dev environment

### Option A — Dev Container (recommended)

A fully configured Dev Container is provided in `.devcontainer/`. It includes Java 17, Node 20 (via devcontainer feature), and a LocalStack sidecar that auto-creates the demo S3 buckets on startup.

**Requirements:** Docker + a Dev Container-compatible IDE.

> **Docker Desktop setting required:** Go to *Settings → General* and **uncheck "Use containerd for pulling and storing images"**, then apply and restart. With containerd enabled, JetBrains cannot resolve the built image by ID (BuildKit config digest vs. containerd manifest digest mismatch) and the container will fail to start.

#### VS Code

1. Install the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers).
2. Open the repo and choose **Reopen in Container** from the notification or the command palette.

#### IntelliJ IDEA / JetBrains Gateway

1. Ensure the **Dev Containers** plugin is installed (bundled in Ultimate; available from the marketplace in Community).
2. From **JetBrains Gateway**: *Remote Development* → *Dev Containers* → *New Dev Container* → point to the repo root.
   Or from inside IntelliJ: `File` → `Remote Development` → *Dev Containers* → *Create Dev Container*.
3. IntelliJ downloads its remote backend into the container on first open (~500 MB, one-time).

**What happens on start:**

```
1. LocalStack container starts
2. Health check polls until S3 is ready
3. init-aws.sh creates demo-assets and demo-archive buckets
4. App container starts (depends_on localstack healthy)
5. postCreateCommand: cd ui && yarn install
```

**Ports forwarded to your host automatically:**

| Port | Service |
|------|---------|
| `8081` | Spring Boot app |
| `5173` | Vite dev server |
| `4566` | LocalStack S3 |

**Notes:**
- The `gradle-cache` and `localstack-data` named volumes persist across container restarts — Gradle and bucket contents survive rebuilds.
- If you change `devcontainer.json` or `docker-compose.yml`, use *Dev Containers: Rebuild Container* (don't just restart).
- `LOCALSTACK_ENDPOINT=http://localstack:4566` is injected automatically so Spring Boot reaches LocalStack via the compose network.

---

### Option B — Local dev with docker-compose

A `docker-compose.yml` at the repo root starts LocalStack and creates the demo buckets automatically:

```bash
docker-compose up -d
```

`LOCALSTACK_ENDPOINT` defaults to `http://localhost:4566` in `application-dev.yaml`, so no extra config is needed. Stop LocalStack with `docker-compose down` (bucket data persists in the `localstack-data` volume across restarts).

---

## Running the project

```bash
# Spring backend (dev profile activates LocalStack config)
./gradlew :app:bootRun -Dspring.profiles.active=dev

# UI dev server (hot reload, proxies /api → :8081)
./gradlew :ui:yarnDev

# Full production JAR (builds UI, copies dist into static/)
./gradlew :app:bootJar
```

The `bootJar` task automatically runs `yarnBuild` and bundles `ui/dist/` into `classpath:/static/`, so the Spring app serves the frontend at `/`.

## Regenerating the API

Controllers and models are generated from `app/src/main/resources/openapi/api.yaml`. After editing the spec, regenerate:

```bash
./gradlew :app:openApiGenerate
```

Generated sources land in `app/build/generate-resources/main/src/main/java/`. **Do not edit these files directly.**
