# PickSure

PickSure is a native Android dark-store fulfillment MVP. It combines batch shelf navigation, real camera barcode verification, Room-backed offline event storage, WorkManager retries, and a FastAPI API that applies synchronized events idempotently.

## What the demo includes

- Employee login with JWT and picker/supervisor roles.
- Transactional inventory decrement on accepted picks, with a store-scoped inventory read endpoint.
- Assigned order dashboard and a deterministic batch route grouped by aisle, rack, shelf, and SKU.
- CameraX preview with on-device ML Kit barcode recognition. A wrong barcode is rejected and no pick is saved.
- Freshness First FEFO picking: verify the product barcode and lot QR label, recommend the earliest eligible expiry, and block lots below the store-configured shelf-life window.
- Lot traceability from local pick event through server inventory decrement and audit record, plus a supervisor freshness monitor for safe, near-expiry, and expired units.
- Room-first event persistence. The UI clearly distinguishes locally saved events from server-confirmed events.
- WorkManager event upload when connected. Every UUID receives an explicit accepted, duplicate, or rejected result.
- Missing-item reporting, substitution approval requests, supervisor decisions, packing checklist, and audit records.
- FastAPI + SQLAlchemy backend with PostgreSQL in Docker Compose and SQLite for local API development.

## Screens and demo assets

The current repository contains source for the MVP screens and flows. Capture dashboard, scan success/failure, batch picking, supervisor approval, and sync-center screenshots from an emulator before publishing a portfolio release. A demo video and signed APK are release artifacts and are not included in this source snapshot.

## Run the backend

From this repository root:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

The API listens on `http://127.0.0.1:8000`; Swagger is at `http://127.0.0.1:8000/docs`. Docker seeds three demo orders and the product catalog.
The demo store blocks lots with fewer than three days remaining (and lots already at expiry). Adjust `MINIMUM_SHELF_LIFE_DAYS` in `.env` to match the store's operational policy; this setting is a dispatch rule, not a universal safety standard.

Demo accounts:

| Role | Email | Password |
| --- | --- | --- |
| Picker | picker@picksure.demo | PickSure123! |
| Supervisor | supervisor@picksure.demo | PickSure123! |

SQLite local mode:

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
alembic upgrade head
uvicorn app.main:app --reload
```

Local mode writes `backend/picksure.db`. Set `DATABASE_URL`, `JWT_SECRET`, and `MINIMUM_SHELF_LIFE_DAYS` from `.env.example` for a non-demo deployment. Docker applies Alembic migrations before starting the API.

## Open the Android app

Open the `android` directory in Android Studio and sync Gradle. The app targets API 35 and supports API 26+. From PowerShell, run `cd android; .\gradlew.bat assembleDebug` to create a debug APK. The emulator API URL is set in `android/gradle.properties` to `http://10.0.2.2:8000/api/v1/`.

For a physical test device, change `API_BASE_URL` to the development machine's reachable LAN address and keep the phone and API host on the same network. The manifest permits cleartext HTTP for local development; production builds should use HTTPS and a managed secret/configuration process.

## Offline lifecycle

1. Camera recognition decodes the product barcode, then the lot QR; the API checks the lot against FEFO and the store's shelf-life policy.
2. The last fetched tasks stay cached in Room, and a matching pick is inserted with a UUID before the UI reports success.
3. WorkManager waits for a connected network and uploads pending rows in creation order.
4. FastAPI validates assignment, store, barcode, quantity, and task state, then returns one outcome per UUID.
5. Room marks an event server-confirmed only after an accepted or duplicate acknowledgment. Rejections remain visible for review.

This is exactly-once logical event application with idempotency. Network delivery itself is at least once.

## Architecture and data

```text
Compose UI ── CameraX + ML Kit
     │
     ├── Retrofit ── FastAPI ── PostgreSQL
     │                   └──── SQLite local mode
     └── Room queue ── WorkManager ── /api/v1/sync/events
```

See `docs/architecture.md`, `docs/database-schema.md`, `docs/api-design.md`, `docs/offline-sync-design.md`, and `docs/barcode-workflow.md`. The deterministic shelf ordering is a sample layout, not a production warehouse optimizer.
The Freshness First lot policy and its operating assumptions are described in `docs/freshness-first.md`.

## Checks and releases

GitHub Actions includes Android debug assembly and backend bytecode compilation. No test results or APK are claimed in this snapshot. Add JUnit, API integration, Compose UI, and WorkManager tests before treating the project as release-ready. GitHub Releases should contain a signed APK only after release signing and device validation are configured.

## Structure

```text
android/                         Kotlin + Compose application
  app/src/main/java/.../data/    Room, Retrofit, worker, local event repository
  app/src/main/java/.../ui/      Compose workflow and CameraX barcode view
backend/app/api/                 FastAPI route modules
backend/app/models.py            SQLAlchemy schema
backend/migrations/              Alembic baseline
docs/                            Product and engineering notes
```
