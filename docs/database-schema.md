# Database schema

| Table | Purpose and key constraints |
| --- | --- |
| stores | Store identity and location. |
| employees | Name, role, salted password hash, active state, store foreign key, unique email. |
| products | Unique SKU and barcode with display name. |
| shelf_locations | One aisle/rack/shelf location per product. |
| inventory | Store/SKU on-hand count with a unique store/SKU pair; decremented in the same transaction as an accepted pick. |
| inventory_lots | Store/SKU lot code, QR tag, expiry, location, and lot quantity; pick events reference the exact lot for traceability. |
| orders | Customer reference, store, priority, fulfillment status. |
| pick_tasks | Employee assignment and task status, one task per order in this MVP. |
| pick_task_items | Requested and picked quantities plus line status. |
| pick_events | Immutable pick, exception, and substitution action records; unique event UUID and optional inventory-lot foreign key. |
| sync_events | Per-event accepted/rejected result and employee owner for idempotent replay and access checks. |
| substitutions | Original/replacement SKU and approval state. |
| packing_records | Packer, order, and packing state. |
| audit_logs | Employee action, entity, details, and timestamp. |

Android Room caches the last task/item snapshot for restart and offline work. Its event table stores UUID, payload fields, creation time, local delivery state, and last rejection reason. Events are retained after acknowledgment to preserve a local history and support accurate progress after app restart.

The demo store policy blocks dispatch of lots with three or fewer days remaining. Set `MINIMUM_SHELF_LIFE_DAYS` to the store's operational policy; it is a configurable fulfillment rule, not a universal food-safety standard. FEFO sorts the eligible lots by expiry date, then lot code for a stable tie-break.

Run `alembic upgrade head` from `backend/` to apply the schema, including the freshness-lot migration. Docker runs migrations before starting the API. API startup also creates missing tables for a convenient local demo.
