# Healenium Backend Setup

This folder contains the local Docker Compose setup for optional Healenium-Web support in Xealenium.

Xealenium already wraps Selenium with `SelfHealingDriver` where possible. If this backend is running, Healenium gets the second lookup chance after native Selenium and before Xealenium visual healing. If it is not running, the tests continue with raw Selenium plus Xealenium.

## Services

- `postgres-db`: stores Healenium reference selectors, healing data, and report data.
- `healenium`: backend API exposed on `http://localhost:7878`.
- `selector-imitator`: healed selector generation service exposed on `http://localhost:8000`.

The Postgres host port is mapped to `5433` to avoid colliding with a local Postgres on `5432`. Containers still communicate on Docker's internal `5432`.

## Start

From the project root:

```powershell
.\scripts\healenium-start.ps1
```

Or directly:

```powershell
docker compose -f healenium/docker-compose.yml up -d
```

Or through Gradle:

```powershell
.\gradlew.bat --no-daemon healeniumUp
```

Verify:

```powershell
docker compose -f healenium/docker-compose.yml ps
```

or:

```powershell
.\gradlew.bat --no-daemon healeniumStatus
```

Open:

- Healenium report: `http://localhost:7878/healenium/report`
- Selector imitator docs: `http://localhost:8000/docs`

## Stop

```powershell
.\scripts\healenium-stop.ps1
```

Or directly:

```powershell
docker compose -f healenium/docker-compose.yml down
```

Or through Gradle:

```powershell
.\gradlew.bat --no-daemon healeniumDown
```

## Project Configuration

The Java client config is in:

```text
src/test/resources/healenium.properties
```

Current local defaults:

```properties
heal-enabled = true
hlm.server.url = http://localhost:7878
hlm.imitator.url = http://localhost:8000
```

The legacy `serverHost` and `serverPort` keys are kept for compatibility with older Healenium client behavior.

## Notes

- Docker Desktop must be installed and running before starting the backend.
- The backend is optional for Xealenium demos. If it is unavailable, test setup logs the fallback and continues with Selenium plus Xealenium.
- Generated backend logs and screenshots under this folder are ignored by Git.
