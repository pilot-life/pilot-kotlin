# pilot-kotlin

Kotlin Multiplatform SDK and Compose Multiplatform UI components for
the **Pilot Partner Inventory API** (PIL-2370).

Two artifacts ship from this repo:

| Module | Artifact | What it gives you |
| --- | --- | --- |
| `pilot-partner-sdk` | `life.pilot:pilot-partner-sdk` | KMP client (Android + JVM + iOS) for the partner API: typed APIs, header auth, idempotency, rate-limit retry, typed errors, webhook envelope + HMAC verification. iOS gets the `PilotPartnerSdk.xcframework`. |
| `pilot-partner-ui` | `life.pilot:pilot-partner-ui` | Compose Multiplatform library (Android + iOS) with components that mirror pilot-frontend's event-card and event-detail/ticket-selection patterns. Depends on the SDK. iOS gets the `PilotPartnerUi.framework`. Gradle's metadata resolution picks the per-target artifact automatically (`-android` AAR or `-ios*` klib). |

Use only the SDK on a backend (webhook ingestion, automation, JVM
services). Use both on Android or iOS apps.

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
    implementation("life.pilot:pilot-partner-ui:0.1.0")
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
    PilotPartnerTheme {                       // optional — see "Theming" below
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

### Theming

Fully customizable across both platforms via `PartnerTheme` — all 32
Material 3 color tokens (light + dark), all 15 typography styles, all
3 shape sizes, and an explicit light/dark override. Every field is
nullable, so you only state the slots you change:

```kotlin
val brand = PartnerTheme(
    light = PartnerColorScheme(primary = 0xFF0A66C2, onPrimary = 0xFFFFFFFF),
    dark  = PartnerColorScheme(primary = 0xFF89B7F3, onPrimary = 0xFF002B5C),
    shapes = PartnerShapes(mediumCornerDp = 16f),
)
PilotPartnerTheme(theme = brand) { EventListWithFilters(...) }
```

Or skip `PilotPartnerTheme` entirely and wrap our components in your
own `MaterialTheme(...)` — they only read from CompositionLocal so
they'll inherit your tokens. The full theming guide (including the
Swift call signature for `PilotPartnerUi.shared.eventsScreen(theme:)`)
is in [docs/integration-guide.md § Theming](docs/integration-guide.md#theming).

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

### Request to Attend & Registration

`EventDetailScreen` promotes a **Request to Attend** button when the
event has `rta?.enabled == true`, and renders a **Registration** section
listing every entry in `InventorySnapshot.registrationTicketTypes` with
its own Register button. Both hooks default to reading the API directly
— no closure wiring is required.

```kotlin
EventDetailScreen(
    event = detail,
    inventory = inv,
    onRequestToAttend = { evt -> showRtaSheet(evt) },
    onRegister = { ticketType -> showRegistrationSheet(ticketType) },
    onContinue = { selections -> startPaidCheckout(selections) },
)
```

Drop-in form composables — host them in a `ModalBottomSheet`:

```kotlin
import life.pilot.partner.ui.checkout.RtaFormSheet
import life.pilot.partner.ui.checkout.RegistrationFormSheet

RtaFormSheet(
    onSubmit = { body ->
        scope.launch {
            client.events.requestToAttend(
                eventUuid = event.eventUUID,
                idempotencyKey = IdempotencyKey.generate(),
                body = body,
            )
        }
    },
)

RegistrationFormSheet(
    ticketType = ticketType,
    onSubmit = { body ->
        scope.launch {
            client.events.createRegistration(
                eventUuid = event.eventUUID,
                idempotencyKey = IdempotencyKey.generate(),
                body = body,
            )
        }
    },
)
```

Registration is cart-style: the response carries `status = "CREATED"`
but the underlying row is **PENDING** until the customer completes
checkout on the host's embed. Surface that pending state to the
customer until checkout confirms it.

## Build & test

```bash
./gradlew build                                   # compile both modules (all targets)
./gradlew :pilot-partner-sdk:jvmTest              # SDK unit tests (Ktor MockEngine)
./gradlew :pilot-partner-ui:testReleaseUnitTest   # UI common unit tests (Android variant)
./gradlew :pilot-partner-ui:connectedAndroidTest  # Compose UI tests (emulator)
./gradlew :pilot-partner-sdk:assembleXCFramework  # iOS framework for SwiftPM
./gradlew publishToMavenLocal                     # publish to ~/.m2 for local apps
```

## iOS

The SDK exposes `PilotPartnerSdk.xcframework` and the UI exposes
`PilotPartnerUi.framework`. After running `assembleXCFramework`:

```
pilot-partner-sdk/build/XCFrameworks/release/PilotPartnerSdk.xcframework
```

The framework contains `ios-arm64` and `ios-arm64_x86_64-simulator`
slices. Drop into an Xcode project via Swift Package Manager (point the
package URL at a Pilot-hosted GitHub release) or by manually adding the
`.xcframework` as an embedded binary.

Swift consumer call sites look like:

```swift
import PilotPartnerSdk

let client = PilotPartnerClient.companion.builder()
    .apiKey(value: "pk_live_…")
    .organizationUuid(value: "…")
    .environment(env: PartnerEnvironment.sandbox)
    .build()

Task {
    let page = try await client.events.list(startsAfter: nil, cursor: nil, limit: 20)
    for event in page.events { print(event.name) }
}
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
