# pilot-kotlin

Kotlin SDK and Jetpack Compose UI components for the **Pilot Partner Inventory API** (PIL-2370).

Two artifacts ship from this repo:

| Module | Artifact | What it gives you |
| --- | --- | --- |
| `pilot-partner-sdk` | `life.pilot:pilot-partner-sdk` | Pure Kotlin/JVM client for the partner API: typed APIs, header auth, idempotency, rate-limit retry, typed errors, webhook envelope + HMAC verification. No Android dependencies. |
| `pilot-partner-ui` | `life.pilot:pilot-partner-ui-compose` | Android library with Jetpack Compose components that mirror pilot-frontend's event-card and event-detail/ticket-selection patterns. Depends on the SDK. |

Use only the SDK on a backend (webhook ingestion, automation). Use both on Android.

## Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/pilot-life/pilot-kotlin")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("life.pilot:pilot-partner-sdk:0.1.0")
    // Android only:
    implementation("life.pilot:pilot-partner-ui-compose:0.1.0")
}
```

## SDK quickstart

```kotlin
import life.pilot.partner.sdk.PartnerEnvironment
import life.pilot.partner.sdk.PilotPartnerClient
import life.pilot.partner.sdk.auth.IdempotencyKey
import life.pilot.partner.sdk.error.PartnerException
import life.pilot.partner.sdk.model.*

val client = PilotPartnerClient.builder()
    .apiKey("pk_live_…")
    .organizationUuid("…")
    .environment(PartnerEnvironment.SANDBOX)
    .build()

// 1. List events
val page = client.events.list(limit = 20)

// 2. Inventory (with ETag caching)
val resp = client.events.inventory(eventUuid = "…")
val etag = resp.headers()["ETag"]
val snapshot: InventorySnapshot? = resp.body()

// 3. Hold seats (idempotent — same key replays the original 201)
try {
    val claim = client.claims.create(
        eventUuid = "…",
        idempotencyKey = IdempotencyKey.generate(),
        body = ClaimCreateRequest(
            items = listOf(ClaimItemRequest(ticketTypeUUID = "…", quantity = 2)),
        ),
    )

    // 4. Finalize → payment + order
    val order = client.claims.checkout(
        claimId = claim.claimId,
        idempotencyKey = IdempotencyKey.generate(),
        body = CheckoutRequest(
            patron = CheckoutPatron(email = "patron@example.com"),
            payment = CheckoutPayment(paymentId = "partner-pay-1", claimedAmount = "65.00"),
        ),
    )

    // 5. Retrieve the order + per-ticket QR codes
    val detail = client.orders.get(order.orderUUID)
} catch (e: PartnerException.SoldOut) {
    // … the ticket type is gone; ask the user to pick another
} catch (e: PartnerException.RateLimited) {
    // The SDK already retried up to maxRateLimitRetries times.
    // e.retryAfterSeconds tells you how long to back off before trying again.
}
```

## Webhooks

```kotlin
import life.pilot.partner.sdk.webhooks.HmacVerifier
import life.pilot.partner.sdk.webhooks.WebhookParser
import life.pilot.partner.sdk.webhooks.WebhookPayload

val verifier = HmacVerifier(secret = System.getenv("PILOT_WEBHOOK_SECRET"))
val parser   = WebhookParser()

// In your HTTP handler:
fun handleWebhook(rawBody: String, signatureHeader: String?) {
    if (!verifier.verify(rawBody, signatureHeader)) {
        return // 401
    }
    when (val payload = parser.parse(rawBody)) {
        is WebhookPayload.InventoryDelta -> updateLocalInventory(payload.data)
        is WebhookPayload.HoldExpired    -> releaseLocalHold(payload.data)
        is WebhookPayload.OrderCreated   -> recordOrder(payload.data)
    }
}
```

## UI components (Android)

The Compose components mirror pilot-frontend's `EventCard.tsx` and the
`EventPage` + `TicketSelectHero` pair: a list of cards, a hero image,
ticket-type rows with quantity steppers, and a sticky-footer
"Continue to Checkout" button that surfaces the running subtotal.

```kotlin
import life.pilot.partner.ui.event.*
import life.pilot.partner.ui.checkout.CheckoutSheet
import life.pilot.partner.ui.theme.PilotPartnerTheme
import life.pilot.partner.ui.viewmodel.EventsViewModel

setContent {
    PilotPartnerTheme {                       // optional — partners can swap their own
        val vm: EventsViewModel = …          // built with the same PilotPartnerClient
        val events by vm.events.collectAsState()

        EventList(
            state = events,
            onLoadMore = { vm.loadMoreEvents() },
            onEventClick = { /* navigate to detail */ },
        )
    }
}
```

### Detail screen

```kotlin
val detail    by vm.detail.collectAsState()
val inventory by vm.inventory.collectAsState()
val error     by vm.detailError.collectAsState()

if (detail != null) {
    EventDetailScreen(
        event = detail!!,
        inventory = inventory,
        error = error,
        onContinue = { selections ->
            // Build a ClaimCreateRequest from `selections` and call the SDK
        },
        onRetry = { vm.loadEvent(eventUuid) },
    )
}
```

### Checkout

```kotlin
CheckoutSheet(
    onSubmit = { patron ->
        scope.launch {
            client.claims.checkout(
                claimId = claim.claimId,
                idempotencyKey = IdempotencyKey.generate(),
                body = CheckoutRequest(patron = patron, payment = …),
            )
        }
    },
)
```

## Build & test

```bash
./gradlew build                      # compile both modules
./gradlew :pilot-partner-sdk:test    # SDK unit tests (MockWebServer)
./gradlew :pilot-partner-ui:testReleaseUnitTest   # UI JVM unit tests
./gradlew :pilot-partner-ui:connectedAndroidTest  # Compose UI tests (emulator)
./gradlew publishToMavenLocal        # publish to ~/.m2 for local apps
```

## Versioning

`VERSION_NAME` lives in `gradle.properties`. Releases publish to GitHub
Packages — see `.github/workflows/publish.yml` (TODO once the org-level
maven repo is provisioned). Until then, snapshots can be pulled from
`mavenLocal()`.

## Reference

- Partner API spec — `pilot-backend` branch `feat/PIL-2370-pr1-foundation`:
  `docs/partner-api/openapi.yaml`
- ADRs that shape this surface:
  - `docs/adr/0008-partner-hmac-signing.md` (webhook signing)
  - `docs/adr/0012-partner-event-dispatcher.md` (webhook envelope, hashes)
  - `docs/adr/0013-partner-idempotency-retention.md`
  - `docs/adr/0017-partner-inventory-api.md`
