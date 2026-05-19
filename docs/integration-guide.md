# Pilot Partner Kotlin SDK & UI — Integration Guide

This is a long-form companion to the [README](../README.md). Use it when
you're wiring the SDK into a real partner app.

## 0. What you get on the classpath

Both artifacts ship a deliberately small set of `api`-scoped transitive
dependencies — anything that appears in a public type signature.
Consuming `life.pilot:pilot-partner-sdk` pulls in:

- `kotlin-stdlib`, `kotlinx-coroutines-core`, `kotlinx-serialization-json`
- `okhttp` and `okhttp-logging-interceptor` (the SDK builder takes
  `HttpLoggingInterceptor.Level` as a parameter)
- `retrofit`

Consuming `life.pilot:pilot-partner-ui-compose` pulls in the SDK plus the
Compose BOM, Material 3, Coil, and `lifecycle-viewmodel-compose` (the
shipped `EventsViewModel` is created via `viewModel(factory = …)`).

You should **not** need to declare these yourself in your app's
`build.gradle.kts`. If a Kotlin import fails to resolve when calling a
documented SDK or UI API, file a bug — that means an `implementation`
dep slipped into a public signature.

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

### Pointing the SDK at a local backend

For partner-side development against a locally-running Pilot backend, a
mock server, or an on-prem deployment, pass an explicit URL via
`.baseUrl(...)` instead of `.environment(...)`. The two are mutually
exclusive — whichever is called last wins on the builder.

```kotlin
PilotPartnerClient.builder()
    .apiKey(BuildConfig.PILOT_API_KEY)
    .organizationUuid(BuildConfig.PILOT_ORG_UUID)
    .baseUrl("http://10.0.2.2:3000/partner/v1/")    // <-- include the path AND the trailing slash
    .build()
```

> **The URL must end with `/partner/v1/` (slash included).** The partner
> API is mounted at that prefix; pilot-backend's root path is GraphQL
> (Apollo). If you set `PILOT_BASE_URL=http://10.0.2.2:3000/`, the SDK
> will hit `/events` instead of `/partner/v1/events` and Apollo's CSRF
> middleware will reject the request with a confusing
> `BadRequestError: This operation has been blocked as a potential
> Cross-Site Request Forgery (CSRF)` JSON body — because Apollo got the
> request, not the partner router.
>
> Quick verify before you build the app:
>
> ```bash
> curl -i http://localhost:3000/partner/v1/health \
>   -H "X-API-Key: $PILOT_API_KEY" \
>   -H "X-Organization-UUID: $PILOT_ORG_UUID"
> # expect: {"ok":true,"version":"v1"}
> ```
>
> If that curl returns the CSRF JSON or a 404, the prefix is wrong on
> your backend or you're hitting the wrong port — fix it there before
> pointing the SDK at it.

### Diagnosing a 404 from `/partner/v1/*`

The partner router intentionally returns `404 NOT_FOUND` for several
distinct conditions — the API never reveals which to prevent
enumeration (ADR-0017). When you see one during integration, the
body content and headers tell you which:

| Response | Likely cause | What to do |
| --- | --- | --- |
| `{"ok":true,"version":"v1"}` on `/health` | All good. The 404 you saw was on a specific event/claim/order UUID that genuinely doesn't exist for this org. | List events first to discover real UUIDs. |
| `{"code":"NOT_FOUND",...}` JSON on `/health` | Partner router is mounted but your org isn't enabled (or API key / org UUID combo is rejected). | Enable `partner_api_enabled` on the org and confirm the API-key↔org binding (see pilot-backend `docs/partner-api/operator-guide.md`). |
| HTML 404 / `Cannot GET /partner/v1/health` | Partner router isn't mounted at this base URL. | Confirm pilot-backend is on a branch with the partner API (`feat/PIL-2370-pr1-foundation` or merged main), then restart. |
| Connect / read timeout | Backend not running or wrong port. | Start the backend; verify the port matches `PILOT_BASE_URL`. |

If you turn the SDK's logging up:

```kotlin
.logging(HttpLoggingInterceptor.Level.BODY)
```

every request and response (including the partner-router 404 JSON) is
printed via Logcat — the fastest way to see which of the cases above
you're in without leaving the app.

Two Android-specific quirks bite here:

1. **`localhost` from the emulator points at the emulator itself, not
   your dev machine.** Use `10.0.2.2` from the standard Android emulator
   (Google's loopback alias for the host) or `host.docker.internal` if
   your backend runs in Docker. Physical devices over USB need
   `adb reverse tcp:3000 tcp:3000` and then `localhost` is fine.

2. **Android blocks cleartext HTTP by default** (since API 28). Hitting
   `http://10.0.2.2:3000` will fail with
   `java.net.UnknownServiceException: CLEARTEXT communication … not permitted`.
   Add a network-security-config that whitelists only the loopback hosts:

   ```xml
   <!-- app/src/main/res/xml/network_security_config.xml -->
   <network-security-config>
       <base-config cleartextTrafficPermitted="false">
           <trust-anchors><certificates src="system" /></trust-anchors>
       </base-config>
       <domain-config cleartextTrafficPermitted="true">
           <domain includeSubdomains="false">localhost</domain>
           <domain includeSubdomains="false">127.0.0.1</domain>
           <domain includeSubdomains="false">10.0.2.2</domain>
       </domain-config>
   </network-security-config>
   ```

   ```xml
   <!-- AndroidManifest.xml -->
   <application android:networkSecurityConfig="@xml/network_security_config" …>
   ```

   This keeps HTTPS-only for every real host while allowing cleartext
   to the three loopback aliases — the right shape for both dev and
   production from one APK.

### Configuring secrets on Android

**Do not call `System.getenv("PILOT_API_KEY")` from your Android app
code.** Android app processes are forked from zygote and do not inherit
your shell's environment — `System.getenv` returns `null` for anything
you set in `.zshrc` / CI env. It works in JVM unit tests (which run in
your dev shell) and silently fails on-device, which is the worst
possible failure mode.

The right pattern is to resolve secrets at **build time** and expose them
via `BuildConfig`:

```kotlin
// app/build.gradle.kts
import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String =
    providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: localProps.getProperty(name)
        ?: ""

android {
    defaultConfig {
        buildConfigField("String", "PILOT_API_KEY",  "\"${secret("PILOT_API_KEY")}\"")
        buildConfigField("String", "PILOT_ORG_UUID", "\"${secret("PILOT_ORG_UUID")}\"")
        buildConfigField("String", "PILOT_GATEWAY_SECRET", "\"${secret("PILOT_GATEWAY_SECRET")}\"")
        buildConfigField("String", "PILOT_BASE_URL", "\"${secret("PILOT_BASE_URL")}\"")
    }
    buildFeatures { buildConfig = true }
}
```

Recognized variables:

| Name | Required | Notes |
| --- | --- | --- |
| `PILOT_API_KEY` | yes | Issued by Pilot ops. |
| `PILOT_ORG_UUID` | yes | The org the key is bound to. |
| `PILOT_GATEWAY_SECRET` | only on dev/sandbox without Oathkeeper | Empty in prod. |
| `PILOT_ENVIRONMENT` | no | `PRODUCTION` / `SANDBOX` / `STAGING` / `DEV`. Defaults to `SANDBOX` if blank. |
| `PILOT_BASE_URL` | no | Overrides `PILOT_ENVIRONMENT` when set — for localhost / mock / on-prem. See "Pointing the SDK at a local backend" below. |

Then at the call site:

```kotlin
val client = PilotPartnerClient.builder()
    .apiKey(BuildConfig.PILOT_API_KEY)
    .organizationUuid(BuildConfig.PILOT_ORG_UUID)
    .gatewaySecret(BuildConfig.PILOT_GATEWAY_SECRET.takeIf { it.isNotBlank() })
    .environment(PartnerEnvironment.SANDBOX)
    .build()
```

Lookup precedence — pick whichever fits your environment:

| Source | When to use | Example |
| --- | --- | --- |
| `-P` Gradle property | One-off local builds, CI invocation | `./gradlew assembleDebug -PPILOT_API_KEY=pk_…` |
| Environment variable | CI build agents | `PILOT_API_KEY=pk_… ./gradlew assembleRelease` |
| `local.properties` | Dev workstation default — **already gitignored by AGP** | `PILOT_API_KEY=pk_test_…` |

Treat production keys like any other release secret: store them in your
CI's secret manager (GitHub Actions Secrets, Vault, AWS Secrets Manager,
Doppler) and inject them as env vars at build time. Never commit them to
`gradle.properties` or check `local.properties` into git.

> ⚠️ `BuildConfig` strings are embedded in the APK as constants. They are
> obfuscated by R8 but **trivially recoverable** by anyone who unzips the
> APK. The partner API key is a bearer credential; rotate immediately if
> a key is leaked. For higher-value secrets (e.g. partner-side payment
> gateway tokens), fetch them at runtime from your own backend instead
> of embedding them.

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

## 7a. Search, date filters, and images

`EventListWithFilters` is a drop-in replacement for `EventList` that
adds a search field and two date chips (start / end) above the list:

```kotlin
val state    by vm.events.collectAsState()
val filters  by vm.filters.collectAsState()

EventListWithFilters(
    state = state,
    filters = filters,
    onFiltersChange = vm::updateFilters,
    imageUrlFor = { evt -> imageResolver.urlFor(evt.eventUUID) },
    onLoadMore = { vm.loadMoreEvents() },
    onEventClick = onEventClick,
)
```

### Filter semantics

The partner API only supports filtering by `startsAfter`. The other
filters apply in-memory. `EventsViewModel.updateFilters(...)` follows
this contract automatically:

| Filter | Where it's enforced | Effect of changing it |
| --- | --- | --- |
| `startsAfter` | Server (`?startsAfter=` on `GET /events`) | Resets pagination, refetches page 1. |
| `endsBefore` | Client | Re-renders the current page filtered in memory. |
| `query` | Client | Same — matches `name` or `venueName` (case-insensitive, trimmed). |

That asymmetry has two consequences worth surfacing to your users:

1. **A client-side filter can leave a page looking empty even when more
   results exist server-side.** `EventListWithFilters` shows a hint
   ("No events match your filters in the current page — scroll to
   load more, or relax the filters.") and continues to paginate.
2. **Search results are scoped to what's been loaded.** A query that
   matches an event 50 pages down won't surface until the user scrolls.
   If you need full-set search, you'll have to maintain your own index
   downstream — the partner API does not expose a search endpoint.

### Event images

The partner API returns `imageUrl: String?` directly on `EventListItem`
and `EventDetail`. The UI components consume it automatically — both
`EventList` / `EventListWithFilters` default `imageUrlFor` to
`{ it.imageUrl }`, and `EventDetailScreen` defaults its `imageUrl`
parameter to `event.imageUrl`. **No wiring required for the common case.**

Override the resolver only if you want a different image source —
typically when your own CMS holds higher-resolution variants or you
need to swap in localized art:

| Override scenario | Example |
| --- | --- |
| Higher-res variants from your CDN | `imageUrlFor = { evt -> cdn.hero(evt.eventUUID) ?: evt.imageUrl }` |
| Region-specific artwork | `imageUrlFor = { evt -> i18n.image(evt.eventUUID, locale) ?: evt.imageUrl }` |
| Force-disable images for a build flavor | `imageUrlFor = { null }` |

When the resolver (or `event.imageUrl`) returns `null`, the card falls
back to a calendar-icon placeholder on the surface-variant color so the
layout stays stable. No network requests are made for the missing image.

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

## 9. Network errors and timeouts

Every failure the SDK can throw — HTTP error response, connect failure,
TLS error, DNS lookup, **read timeout** — surfaces as a
`PartnerException` subclass. Consumers never need to catch raw
`IOException` from a documented SDK call.

Transport-level failures all map to `PartnerException.Network`:

```kotlin
import life.pilot.partner.sdk.error.PartnerException

try {
    client.events.list()
} catch (e: PartnerException.Network) {
    // No internet, backend unreachable, DNS broken, read timed out.
    // e.cause holds the underlying SocketTimeoutException /
    // UnknownHostException / ConnectException for inspection or logging.
    showSnackbar("Couldn't reach Pilot — ${e.message}")
} catch (e: PartnerException.NotFound) {
    // …
} catch (e: PartnerException) {
    // Catch-all for any other typed failure.
}
```

### Timeout knobs

The builder exposes the two safe-to-tune timeouts:

| Knob | Default | Tune up when … |
| --- | --- | --- |
| `connectTimeout(seconds)` | `10` | Customer base on flaky / high-latency networks (rural, in-venue Wi-Fi). |
| `callTimeout(seconds)` | `30` | Background workers willing to wait for slow flows. **Do not raise** on user-blocking screens — show a spinner and fail fast. |

The underlying OkHttp `readTimeout` defaults to `10s` and is the timeout
that **actually fires in production when the backend hangs** — it
manifests as a `SocketTimeoutException` propagated through the SDK and
arrives at your `catch` as `PartnerException.Network`. If you need a
different read timeout, set it via the escape hatch:

```kotlin
.configureHttpClient { it.readTimeout(15, TimeUnit.SECONDS) }
```

> ⚠️ Avoid `callTimeout` for "kill the request entirely". When OkHttp's
> call-timeout watchdog fires, the resulting `InterruptedIOException`
> escapes the SDK's typed-exception wrapping (it's thrown from a layer
> above the interceptor chain). Prefer `readTimeout` / `connectTimeout`
> for tight bounds, and use `callTimeout` only as a generous outer
> safety net.

### Don't only catch `PartnerException` in coroutines

A common ViewModel bug: catching only one exception type inside a
`viewModelScope.launch { }` and letting everything else crash the app.
Always rethrow `CancellationException`, then widen the catch:

```kotlin
viewModelScope.launch {
    try {
        val page = client.events.list()
        // …
    } catch (e: CancellationException) {
        throw e               // never swallow cancellation
    } catch (e: Throwable) {
        _state.update { it.copy(error = e.message) }
    }
}
```

`EventsViewModel` in `pilot-partner-ui` already does this — it's the
template to copy.

## 10. R8 / ProGuard

The SDK ships consumer R8 rules inside the JAR at
`META-INF/proguard/pilot-partner-sdk.pro`. AGP picks them up automatically
when minification runs — you do **not** need to copy or repeat them in
your app's `proguard-rules.pro`.

What they cover:

- All `@Serializable` model + webhook classes and their generated
  `$$serializer` helpers (otherwise R8 strips them and JSON parsing
  silently fails at runtime with `SerializationException: Serializer for
  class … is not found`).
- Retrofit API interfaces — annotations are kept so reflection at
  call-site works.

The UI module ships matching rules at `consumer-rules.pro` covering the
same types and the SDK's webhooks.

**Verify before shipping**: enable minification in a sandbox build and
run the integration smoke test (see `pilot-kotlin-test/app/src/test/.../SdkIntegrationSmokeTest.kt`).
If `client.events.list()` parses an empty `nextCursor` correctly with R8
enabled, the rules are working.

## 11. Going to production

Checklist:

- [ ] API key + org UUID stored in a secrets manager, not source.
- [ ] `environment(PartnerEnvironment.PRODUCTION)` (not `SANDBOX`).
- [ ] Webhook endpoint behind HTTPS with the HMAC verifier wired in.
- [ ] Idempotency keys persisted alongside the local hold record.
- [ ] Rate-limit retries tuned for your traffic profile.
- [ ] Observability — wrap the client with an OkHttp interceptor that
  emits your metrics (`configureHttpClient { it.addInterceptor(…) }`).
