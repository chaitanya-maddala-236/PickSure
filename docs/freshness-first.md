# Freshness First: the PickSure differentiator

## Real-world problem

Quick-commerce orders can be packed with stock that is technically in date but expires soon after delivery. At the same time, dark stores often have multiple batches of the same SKU on one shelf. A picker needs a fast way to choose the right SKU and the right batch while the store keeps track of short-dated inventory.

PickSure makes each pick verify both the product barcode and its lot QR label. The API chooses the earliest-expiring lot that meets the store's minimum remaining shelf-life policy (FEFO), and rejects an expired, near-expiry, mismatched, depleted, or no-longer-recommended lot. The same transaction decrements aggregate inventory and the selected lot; the pick event keeps the lot reference for audit and traceability.

## Picker workflow

1. Open a shelf-ordered batch and follow the aisle/rack/shelf route.
2. Scan the product barcode. A mismatch creates no pick.
3. Scan the recommended lot QR. The screen displays the lot code and expiry before the pick is saved.
4. PickSure writes the event to Room with its own UUID, product barcode, lot ID, and QR payload.
5. WorkManager retries the event when connected. The API rechecks the product, lot, FEFO order, shelf-life policy, assignment, and quantity before changing stock.
6. The local event changes to server-confirmed only after an accepted or duplicate acknowledgment.

## Supervisor workflow

The freshness monitor shows dispatchable units, held-back near-expiry units, expired units, and the remaining stock for each lot. It also shows the lot QR payload used by the scanner, so the store can encode that value on its own receiving or shelf label.

## Demo data and policy

The demo seeds each product with a near-expiry lot and a safe lot. Lot codes use the current year. Near-expiry stock is deliberately left in aggregate inventory but is excluded from task recommendations and pick acceptance.

`MINIMUM_SHELF_LIFE_DAYS` defaults to `3`. A lot is eligible when it has at least that many calendar days remaining; lots expiring today or already past expiry are blocked. A store should configure this rule to its own operational/customer promise. It is not a universal food-safety or legal standard. Expiry is assessed using the API host's calendar date.

For a camera demo, print the QR payload shown in the monitor as a QR code or display it on a second device. The phone running the picker must still scan the real product barcode and lot QR through CameraX/ML Kit; there is no simulated scan action.

## Honest limitations

- The shelf map and demo stock are seeded examples, not a live Blinkit or warehouse connection.
- A task snapshot can become stale while a picker is offline. The server rechecks stock at sync and leaves rejected events in the review queue.
- This MVP assigns one recommended lot per task line. A production flow that splits one line across lots should model each picked quantity as a separate lot allocation.
