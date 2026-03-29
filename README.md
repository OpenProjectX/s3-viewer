# S3 Viewer

Multi-cloud S3 browser built with Spring Boot WebFlux, Kotlin, OpenAPI-generated controllers, and a React + MUI frontend.

## Backend

- `app`: Spring Boot application and OpenAPI contract
- `autoconfigure`: configuration properties and default `S3ViewerService`
- `core`: shared domain/service contracts
- `starter`: starter module for the application

The backend exposes:

- `GET /api/v1/providers`
- `GET /api/v1/providers/{providerId}/buckets`
- `GET /api/v1/providers/{providerId}/buckets/{bucketName}/browse?path=...`

Provider configuration lives under `s3-viewer.providers` in [application.yaml](/data/Git/s3-viewer/app/src/main/resources/application.yaml).

## UI

`ui` is a Vite + React + MUI app with a three-panel file-browser layout for providers, buckets, and object listings.

Useful tasks:

- `./gradlew :app:bootRun`
- `./gradlew :ui:yarnDev`
- `./gradlew :ui:yarnBuild`
