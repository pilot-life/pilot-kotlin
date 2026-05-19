# Pilot Partner Kotlin SDK & UI — Integration Guide

This is a long-form companion to the [README](../README.md). Use it when
you're wiring the SDK into a real partner app.

## 1. Provisioning

Before the SDK can do anything, your team needs:

1. **API key** (`X-API-Key`) — issued by Pilot ops, bound to a specific
   organization tree.
2. **Organization UUID** (`X-Organization-UUID`) — the host org for the
   integration. A mismatched UUID returns `404 NOT_FOUND` (cross-tenant
   safety).
3. **Webhook secret** — used by `HmacVerifier` to validate webhook
   payloads. Treat as a bearer credential.
4. **Gateway secret** (dev/sandbox only) — set on `X-Gateway-Secret`
   when hitting the backend directly. In production, traffic flows
   through Oathkeeper which injects this header for you.

```kotlin
val client = PilotPartnerClient.builder()
    .apiKey(BuildConfig.PILOT_API_KEY)
    .organizationUuid(BuildConfig.PILOT_ORG_UUID)
    .gatewaySecret(BuildConfig.PILOT_GATEWAY_SECRET.takeIf { it.isNotBlank() })
    .environment(PartnerEnvironment.SANDBOX)
    .build()
```

`PartnerEnvironment` ships with `PRODUCTION`, `SANDBOX`, `STAGING`, `DEV`.
Use `.baseUrl(...)` for self-hosted/test rigs.

## 2. The full purchase flow

The partner API uses a **two-phase commit**:

```
   GET /events/{uuid}/inventory  ─►  see what's available, get ETag
              │
              ▼
   POST /events/{uuid}/claims    ─►  hold seats for 10 minutes
              │  (Idempotency-Key required)
              ▼
   POST /claims/{id}/checkout    ─►  finalize → payment + order
              │  (Idempotency-Key required)
              ▼
   GET /orders/{uuid}            ─►  retrieve QR codes
```

Each step has its own failure mode:

| Step | Common failure | Typed exception |
| --- | --- | --- |
| inventory | event not owned by your org | `PartnerException.NotFound` |
| claim | the ticket type is gone | `PartnerException.SoldOut` (carries `ticketTypeUUID`) |
| claim | replay with different body | `PartnerException.IdempotencyConflict` — use a NEW key |
| claim | concurrent writer in flight | `PartnerException.IdempotencyInProgress` — retry without changing the key |
| checkout | 10-min TTL elapsed | `PartnerException.ClaimExpired` (carries `status`) |
| checkout | partner POS didn't verify | `PartnerException.PaymentVerificationFailed` (carries `reason` + `partnerReason`) |
| any | per-API-key throttle | `PartnerException.RateLimited` (after the SDK exhausts retries) |

## 3. Idempotency

Every mutating route (`POST /claims`, `POST /checkout`) **requires** an
`Idempotency-Key` header. The contract:

- **Same key + same body** → cached `201` is replayed.
- **Same key + different body** → `409 IDEMPOTENCY_CONFLICT`. Generate
  a fresh key.
- **Same key, still processing** → `409 IDEMPOTENCY_IN_PROGRESS`. Wait
  briefly, then retry with the **same** key.

`IdempotencyKey.generate()` returns a UUIDv4. Persist the key alongside
the local hold record so retries can replay safely after process restart.

```kotlin
val key = idemKeyStore.getOrPut(holdId) { IdempotencyKey.generate() }
val claim = client.claims.create(eventUuid, key, request)
```

## 4. Rate limiting

`/partner/v1/*` is rate-limited per API key. The SDK automatically
retries up to `maxRateLimitRetries` (default `2`) honoring `Retry-After`.

If retries are exhausted, the SDK throws `PartnerException.RateLimited`
with `retryAfterSeconds`. Surface this to callers — don't loop in-app.

```kotlin
.maxRateLimitRetries(3)        // raise it if your background worker can wait
.configureHttpClient { ok ->   // add an OkHttp Dispatcher cap if needed
    ok.dispatcher().maxRequestsPerHost = 4
}
```

## 5. ETag caching for inventory

`GET /events/{uuid}/inventory` returns an `ETag` header. Send it back on
the next request via `If-None-Match` and receive `304 Not Modified` when
nothing has changed — significant bandwidth win on polling loops.

```kotlin
val resp = client.events.inventory(eventUuid, ifNoneMatch = lastEtag)
when (resp.code()) {
    200 -> { lastEtag = resp.headers()["ETag"]; render(resp.body()!!) }
    304 -> { /* keep showing cached snapshot */ }
}
```

`EventsViewModel.refreshInventory` does this already.

## 6. Webhook delivery

Partner-API webhooks fire on three events (`inventory.delta`,
`hold.expired`, `order.created`). The envelope is:

```json
{
  "eventId":   "uuid",            // dedup on this
  "eventType": "order.created",
  "createdAt": "2026-…",
  "data":      { … }              // type-specific
}
```

Sign verification (HMAC-SHA256 over `"{timestamp}.{rawBody}"`):

```kotlin
val verifier = HmacVerifier(
    secret = secret,
    toleranceSeconds = 5 * 60,     // 5-min replay window
)
if (!verifier.verify(rawBody, request.header("X-Pilot-Signature"))) {
    return Response.status(401).build()
}
```

**Always** dedup on `eventId` — webhooks are at-least-once.

## 7. UI component conventions

The Compose components mirror pilot-frontend's hierarchy:

| Pilot Frontend (React) | Pilot Kotlin (Compose) |
| --- | --- |
| `EventCard.tsx` | `EventListItemCard` |
| `EventPage.tsx` + `TicketSelectHero` | `EventDetailScreen` |
| `TicketBookingCard.tsx` (per-ticket row) | `TicketTypeRowItem` |
| Patron form on checkout step | `CheckoutSheet` |

### Hoisted state

Selection state lives in `TicketSelectionState`, hoisted by default with
`rememberTicketSelectionState()`. If you need to persist across nav, hoist
it into a ViewModel and pass it down.

### Theming

`PilotPartnerTheme` is a thin `MaterialTheme` wrapper. Components only
read `MaterialTheme.colorScheme` and `typography`, so partners with their
own design system can wrap the components in their own `MaterialTheme`
and the components will follow.

### Test tags

Every interactive element exposes a stable `testTag` (e.g.
`TicketTypeRowItemTestTags.incFor(uuid)`). Use them in Compose UI tests
without depending on user-facing text.

## 8. Threading & lifecycle

- All SDK APIs are `suspend fun` — call them from a coroutine scope.
- Reuse one `PilotPartnerClient` per app process. The underlying OkHttp
  client pools connections and threads — making a new client per request
  defeats both.
- Call `client.close()` on app shutdown if you want graceful release of
  the OkHttp dispatcher (optional — leaking it during process death is
  harmless).

## 9. Going to production

Checklist:

- [ ] API key + org UUID stored in a secrets manager, not source.
- [ ] `environment(PartnerEnvironment.PRODUCTION)` (not `SANDBOX`).
- [ ] Webhook endpoint behind HTTPS with the HMAC verifier wired in.
- [ ] Idempotency keys persisted alongside the local hold record.
- [ ] Rate-limit retries tuned for your traffic profile.
- [ ] Observability — wrap the client with an OkHttp interceptor that
  emits your metrics (`configureHttpClient { it.addInterceptor(…) }`).
