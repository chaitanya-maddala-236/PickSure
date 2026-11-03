# API design

All application routes are under `/api/v1`. Interactive OpenAPI documentation is at `/docs`.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/login` | Employee JWT login. |
| GET | `/tasks` | Tasks visible to the signed-in employee. |
| GET | `/tasks/{id}` | Task summary. |
| GET | `/tasks/{id}/items` | SKU, barcode, quantity, shelf details, and earliest eligible lot recommendation. |
| POST | `/scan/verify` | Compare a decoded barcode with the requested line. |
| POST | `/scan/verify-lot` | Verify lot QR tag, SKU/store, expiry eligibility, available units, and current FEFO recommendation. |
| GET | `/inventory/freshness` | Store-scoped safe, near-expiry, and expired lot counts plus lot-level traceability. |
| GET | `/inventory/{sku}` | Read on-hand inventory for the authenticated employee's store. |
| POST | `/pick-events` | Record one or more pick events. |
| POST | `/items/{id}/exception` | Report an unavailable line. |
| POST | `/substitutions` | Request or record an approved replacement. |
| GET | `/substitutions/pending` | List store-scoped approval requests for supervisors/admins. |
| POST | `/substitutions/{id}/approve` | Approve or reject a request; approval switches the task line to the replacement SKU for barcode picking. |
| POST | `/sync/events` | Batch upload with an individual outcome for every event UUID. |
| GET | `/sync/events/{uuid}` | Check the saved result for one event. |
| POST | `/tasks/{id}/pack` | Confirm packing after checklist validation. |
| GET | `/audit-logs` | Store-scoped supervisor/admin audit view. |

Every batch result is `accepted`, `duplicate`, or `rejected`, with a message and optional server event id. A retry of a previously accepted event returns `duplicate`; the server does not increment the quantity again. Rejected results are persisted too, so the client can keep them in a review state rather than retrying forever.

Each PICK event carries both the product barcode and the scanned `lot_id`/`lot_tag`. The API rechecks FEFO eligibility and decrements that lot and aggregate SKU stock in the same transaction.
