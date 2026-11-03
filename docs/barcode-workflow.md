# Barcode workflow

1. The selected task line supplies the expected SKU and barcode from the local task snapshot.
2. CameraX supplies live camera frames to ML Kit's on-device barcode scanner.
3. The first scan compares the decoded product barcode with the selected item's expected barcode.
4. A product mismatch displays an error and creates no pick event.
5. After a product match, the picker scans the lot QR tag. `/scan/verify-lot` checks that it belongs to the SKU/store, is in stock, meets the store's shelf-life policy, and is the earliest eligible FEFO lot.
6. The pick is saved to Room with a fresh UUID, the product barcode, and the lot id/tag. The server repeats SKU, lot, FEFO, assignment, store, and quantity validation when the event synchronizes.

If the API is unavailable, the app compares the product barcode and lot tag with the last task snapshot and still saves the action locally. The server makes the final decision when the event syncs, and a changed recommendation can be refreshed and rescanned.

The camera implementation uses real CameraX frames and ML Kit recognition. There is no simulated scan path. Camera permission is requested at runtime; if it is denied, the picker must grant it before scanning.
