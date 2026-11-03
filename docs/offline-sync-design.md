# Offline synchronization

```mermaid
sequenceDiagram
  participant Picker
  participant App
  participant Room
  participant Worker as WorkManager
  participant API as FastAPI
  Picker->>App: Scan expected product barcode
  App->>App: Scan recommended FEFO lot QR
  App->>Room: Insert UUID + product + lot in transaction
  App-->>Picker: Saved locally, pending sync
  Worker->>Room: Read oldest pending events
  Worker->>API: Upload batch with UUIDs
  API->>API: Validate and apply each event once
  API-->>Worker: Result per UUID
  Worker->>Room: Mark acknowledged or needs attention
  App-->>Picker: Show server-confirmed state
```

The Room insert completes before the success state is shown. A connectivity-constrained WorkManager request sends up to 50 events in creation order. Transport failures keep events pending and retry with WorkManager backoff. An accepted response marks only that UUID synced. A validation rejection moves only that UUID to `NEEDS_ATTENTION`; the event and error remain available, and the picker can explicitly retry after reviewing it. If no employee token is stored, events remain pending until sign-in schedules work.

The server stores each UUID under a unique constraint and commits each event separately. The synchronization guarantee is exactly-once logical application through idempotency, not exactly-once network delivery.
