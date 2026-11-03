# PickSure product brief

PickSure is an offline-first Android fulfillment tool for dark-store pickers and packers. It reduces wrong-SKU picks and lost work by pairing camera-based barcode checks with a durable local event queue.

## Users and workflow

- Pickers sign in, review assigned orders, follow aisle/rack/shelf directions, scan each requested product, and report unavailable items.
- Packers verify each order checklist and record completion.
- Supervisors can review store-scoped audit events and approve substitutions.
- Admins are represented in role checks; employee and catalog provisioning are seeded or managed outside this MVP.

## MVP capabilities

- JWT employee sign-in with picker and supervisor roles.
- Task list and per-task item details from the API, with an offline demo dataset.
- Deterministic batch route grouped by aisle, rack, shelf, then SKU.
- CameraX live preview with ML Kit product barcode decoding and expected-SKU comparison.
- Room persistence before a pick is acknowledged in the UI.
- WorkManager network-constrained event delivery, per-event results, idempotency UUIDs, retry, and an attention state for server rejections.
- Missing-item reasons, pending substitution approval, packing checklist, and audit history.

## Operational rules

- A scan is recorded only after the decoded barcode matches the selected SKU.
- A local save and server confirmation are separate states.
- The client retains pending and rejected events; sync failure never deletes an event.
- The server validates assignment, store access, barcode, quantity, and packing readiness.
- A rejected event remains visible for review. Corrected work should be recorded as a new event with a new UUID.

## MVP boundaries

Shelf positions are seeded sample data, route ordering is deterministic and not a warehouse optimizer, and replacement approvals are represented as workflow records. Production roll-out needs managed employee provisioning, secret rotation, operational monitoring, real catalog/task ingestion, device enrollment, and privacy/security review.
