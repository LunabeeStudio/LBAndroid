---
issue: LBA-3
phase: review
type: feature
wiki: [feedback, architecture, build-and-publishing]
note: First consumer oneSafe 6 devBeta (separate PR in oneSafe6).
created: 2026-11-18
---

# In-app feedback to Plane intake

## Context / Decisions

An Android (and later iOS) app lets the client, the PO and testers send feedback into a Plane project's Intake
queue. First consumer: oneSafe 6, `devBeta` only.

Plane facts (Lunabee Plane CE v1.5.0, `projects.lunabee.ovh`, workspace `lunabee`):

- Create: `POST /api/v1/workspaces/{slug}/projects/{project_id}/intake-issues/` with
  `{"issue": {"name", "description_html", "priority"}}`, header `X-API-Key`. No labels on create.
- Read / edit / delete: `GET|PATCH|DELETE …/intake-issues/{issue_id}/`. A Guest may edit only the name and description
  of its own items, and delete them. Intake status: `-2` pending, `-1` declined, `0` snoozed, `1` accepted,
  `2` duplicate.
- Attachments (verified in `apps/api/plane/api/views/issue.py:2025` at tag `v1.5.0`): a Guest creator is allowed.
  The upload has 3 steps:
  1. `POST …/work-items/{issue_id}/attachments/` with `{name, type, size}` returns a presigned POST (`upload_data`)
     and the asset id.
  2. Multipart POST of the file to `upload_data.url` with `upload_data.fields`.
  3. `PATCH …/work-items/{issue_id}/attachments/{asset_id}/` with `{"is_uploaded": true}`.
- Limits: 5 MB per upload, about 60 requests per minute per key (429 above).
- Bot setup: a dedicated Plane user, Guest of the target project only, `guest_view_all_features` off, intake
  enabled. One shared token, so the server cannot tell testers apart. The library filters "my feedback" locally.

Decisions taken while grilling (2026-11-18):

| Topic | Decision |
|---|---|
| Modules | `feedback-core` + `feedback-plane` + `feedback-ui`, under `common/feedback/`, group `studio.lunabee.feedback` |
| Platforms | KMP (Android + iOS). UI in Compose Multiplatform, like `monitoring-ui` |
| Triggers | Public API (`open()` + composables), opt-in persistent notification, opt-in shake. Notification and shake are Android only |
| oneSafe variants | `devBeta` only. `storeBeta` (public beta) gets nothing until a relay transport exists |
| Failure | No queue. The draft, including the screenshot, stays in memory. The user taps Retry. Nothing goes to disk |
| My feedback | Local list of sent items, with live status, edit and delete |
| Reporter | Optional "Your name" field, remembered locally, written into the description |
| Token | Long-lived, no expiry. Rotated by hand if it leaks. A 401 still shows a clear error |
| Screenshot | Off by default. Capture the current screen, or pick from the gallery. Redact tool (black boxes). Preview before sending |
| Fields | Type (Bug / Idea / Other, sent as a `[Bug] ` title prefix) + title + description. The PO sets priority |
| Android host | Library-owned `LBFeedbackActivity`, plus public composables to embed in an app's NavHost |
| Theme | Inherit the app's `MaterialTheme`. The Activity takes an optional theme wrapper from config |

Assumptions to confirm (not grilled):

- **No DI framework.** The library wires itself by hand (`LBFeedback.install(config)` builds the graph). This avoids
  forcing Koin or Hilt on consumers. oneSafe uses Hilt.
- **Edit and delete only while pending.** Once the PO accepts, declines or marks an item as a duplicate, the app
  shows it read-only. This prevents a tester from overwriting the PO's triage edits.
- **The description is rebuilt from local data on edit.** The local store keeps the title, body, type, reporter and
  metadata of each sent item. An edit regenerates `description_html` instead of parsing Plane's HTML. This is tester
  text only, never vault data.
- **iOS v1 scope:** `LBFeedbackViewController()`, current-screen capture and the API trigger. Gallery pick, shake and
  notification are Android only in v1.
- **Partial success:** if the intake item is created but the screenshot upload fails, the item is kept. The form
  offers "Retry screenshot", which repeats only the attachment upload.
- **Strings:** EN + FR as Compose resources.
- **Draft model name (autopilot assumption):** the core draft class is `LBFeedbackDraft`, not `LBFeedback`.
  `LBFeedback` stays the entry point object (`install`, `open`), so one name never means two things. Sent items are
  `LBFeedbackItem`, intake status is `LBFeedbackStatus`.
- **BOM bump (autopilot assumption):** new modules are a minor BOM bump, `4.19.0` (released) → `4.20.0`.

## Architecture

```
feedback-core  (KMP: commonMain, androidMain, iosMain)
  LBFeedbackConfig, LBFeedback (draft), LBFeedbackType, LBFeedbackMetadata, LBFeedbackAttachment
  LBFeedbackTransport            interface: send / get / update / delete / attach
  LBFeedbackError                Unauthorized, RateLimited, Network, TooLarge, Server, NotFound
  LBFeedbackStore                sent items + reporter name (DataStore preferences, KMP)
  LBFeedbackMetadataProvider     expect/actual: device model, OS version, locale + app-supplied info
feedback-plane (KMP)
  LBPlaneFeedbackTransport(baseUrl, workspaceSlug, projectId, token: suspend () -> String, httpClient)
  description_html builder (escaped user text + metadata table)
feedback-ui    (KMP + CMP)
  LBFeedbackFormScreen, LBFeedbackListScreen, LBFeedbackRedactScreen (composables + ViewModels)
  androidMain: LBFeedbackActivity (FLAG_SECURE), screen capture, photo picker,
               LBFeedbackNotification, LBFeedbackShakeTrigger
  iosMain:     LBFeedbackViewController, key window capture
```

`LBFeedbackTransport` is the seam for a later relay server: a `feedback-relay` module implements the same interface,
and nothing else changes. `feedback-core` never depends on Plane or Ktor.

### Metadata

Every item gets these fields automatically: app version name and code, build variant, device manufacturer and
model, OS version, locale, library version and send time (UTC). The app passes the version and variant in
`LBFeedbackConfig`, because a library cannot read the app's `BuildConfig`.

An optional `extras: suspend () -> Map<String, String>` hook adds more fields, for example the current route. Its
KDoc says it must never carry user data. The library has no log API and attaches no logs.

### Screenshot (Android)

- `install()` registers `ActivityLifecycleCallbacks` to track the resumed activity.
- On trigger, the library captures `window.decorView` with `View.drawToBitmap()`. This draws the app's own views, so
  it works on FLAG_SECURE screens. SurfaceView, video and separate windows (dialogs, popups) are not included.
- The bitmap stays in memory only. It is downscaled and encoded so it stays under 5 MB.
- `LBFeedbackActivity` sets FLAG_SECURE itself, because it shows a capture of a secure screen.
- Photo picker: `PickVisualMedia`, so no permission is needed.
- The redact screen draws opaque rectangles and flattens them into the bitmap before upload.

### Triggers (Android)

- `LBFeedback.open(context)` captures the current screen, then starts `LBFeedbackActivity`.
- `LBFeedbackNotification.show(context)` posts an ongoing notification whose tap opens the form. The app must hold
  `POST_NOTIFICATIONS` on API 33+. The library never asks for it.
- `LBFeedbackShakeTrigger` uses the accelerometer and is lifecycle aware. It is off unless enabled. oneSafe does not
  enable it, because of shake-to-lock.

## oneSafe integration (separate PR in oneSafe6)

- `devImplementation` of `feedback-ui` + `feedback-plane`. The `store` flavor gets nothing: a `src/store` no-op keeps
  `main` code compiling.
- Token: a `buildConfigField` in the `dev` flavor only, read from `providers.gradleProperty("onesafe.feedback.planeToken")`
  with an env fallback for CI. With no token, the feature is hidden and nothing crashes. The token is never committed.
- Entry point: a "Send feedback" row in a dev-only settings section, plus the opt-in notification. No shake.

## Acceptance criteria

- [ ] `common/feedback/feedback-core`, `feedback-plane` and `feedback-ui` build for Android and iOS, publish as
      `studio.lunabee.feedback:feedback-{core,plane,ui}:1.0.0`, and are listed in the BOM, `CHANGELOG.MD` and
      `AndroidConfig.kt`.
- [ ] `LBPlaneFeedbackTransport.send` creates a pending intake item whose title is `[<Type>] <title>`. Its
      description contains the escaped user text, the reporter if set, and the metadata table.
- [ ] With a screenshot, the item carries one uploaded attachment under 5 MB (presigned POST, upload, then
      `is_uploaded`), named `screenshot-<yyyyMMdd-HHmmss UTC>.jpg` from its capture or pick time.
- [ ] If the attachment fails after the item was created, the form keeps the item and offers a retry of the
      screenshot only.
- [ ] 401, 429, network and 5xx errors map to `LBFeedbackError` and show a localized message. The draft is kept, and
      Retry resends it.
- [ ] The screenshot is off by default, previewed before sending, and redactable. Nothing feedback-related is
      written to disk except the local store (sent items, reporter name).
- [ ] The "My feedback" list shows only items sent from this install, with their live intake status. Edit and delete
      work while an item is pending, and are disabled after that.
- [ ] `LBFeedbackActivity` sets FLAG_SECURE. Capture works on a FLAG_SECURE activity in the demo app.
- [ ] The notification and shake triggers open the form, and are off unless the app enables them.
- [ ] `demo-compose` shows the feature with a fake transport, and with the Plane transport when a local token is set.
- [ ] Unit tests cover the Plane transport (`ktor-client-mock`: create, attach, edit, delete, error mapping), the
      description builder (HTML escaping) and the store.
- [ ] README documents the Plane bot setup: Guest role, `guest_view_all_features` off, intake on, and the security
      caveats.
- [ ] An app can attach any files to a feedback (name, MIME type, bytes; each under 5 MB) through the public API,
      before or while the form is open. The form lists them and the user can remove each one. Each file is uploaded
      as one Plane attachment. If some uploads fail after the item was created, the item is kept and only the failed
      files are retried.
- [ ] An app can build its own feedback UI on a public headless API in `feedback-core` (send with attachments, list
      sent items with live status, edit, delete, retry failed attachments) and an Android screen capture that returns
      bytes. The `feedback-ui` screens use that same API.
- [ ] Dark theme: the screens follow the app's `MaterialTheme` when the app wraps them. `LBFeedbackActivity` and
      `LBFeedbackController` follow the system light or dark mode when the app passes no theme wrapper, system
      bars included.
- [ ] The UI matches the approved design mockup (https://claude.ai/artifact/Ks7E2Da28x5AWA7V4i4xD3), and the redact
      screen keeps Undo and Done visible with a portrait screenshot.
- [ ] `./gradlew detekt` and `./gradlew test` pass.

## Risks to verify while coding

- The presigned upload URL must be reachable from a device. On self-hosted CE it should point to
  `projects.lunabee.ovh/uploads`.
- `guest_cannot_view_issue` must let the bot read its own intake items when `guest_view_all_features` is off.
- Capturing the previous activity when the notification opens the form: it is paused, but its decor view is still
  attached.

## Feedback

### Round 1 — 2026-11-19 (during autopilot, before delivery)

- Use the design of the mockup artifact (https://claude.ai/artifact/Ks7E2Da28x5AWA7V4i4xD3).
- Add a way for the client app to attach any files, and make sure apps can implement their own UI.
- Decisions taken by autopilot on these notes (to confirm):
  - "Client app" means the integrating app: it attaches files by API (for example logs it chose to share). The
    built-in form lists them with a remove action. The built-in form gets no generic file picker; the gallery
    picker stays (autopilot assumption).
  - `LBFeedbackDraft.attachment` becomes `attachments: List<LBFeedbackAttachment>`. The screenshot is one of them.
    Files stay in memory only (autopilot assumption).
  - The headless API lives in `feedback-core`, so an app with its own UI never depends on Compose
    (autopilot assumption).
  - The README warns that attached files go to Plane as they are and must never carry user data.

### Round 2 — 2026-11-19

- Asked: "Handle dark theme"
- Triage: adjustment. Screens hosted by the library (`LBFeedbackActivity`, `LBFeedbackController`) with the default
  `themeWrapper = { it() }` had no `MaterialTheme`, so they showed light M3 colors on a dark phone. New criterion
  "Dark theme" added to `## Acceptance criteria`.
- Route: implementer.
- Changed: new public `LBFeedbackTheme` (M3 `lightColorScheme` / `darkColorScheme` by `isSystemInDarkTheme()`) is
  the default wrapper of every `install` overload; `LBFeedbackActivity` calls `enableEdgeToEdge()` and uses
  `Theme.LBFeedback` (platform theme, `values-night` window background = M3 baseline background) so the window
  matches before Compose draws; README "Theming" section; CHANGELOG `feedback-ui` bullet. iOS gets the default
  through `LBFeedback.Themed`. Redaction black stays black on purpose.
- Build: `:feedback-ui:testAndroidHostTest`, iosArm64 + iosSimulatorArm64 compile, `:demo-compose:assembleDebug`,
  detekt ✅. WARNINGS: none new.
- Review: deferred (`review.when: on_validation`). Verify: round 2: manual validation — not run by agent.

### Round 3 — 2026-11-19

- Asked (PR #400 review 5972248526):
  - `4626600404` on `LBFeedbackBitmapDataSource.kt`: "add timestamp in name"
  - `4629716985` on `InMemoryLBFeedbackStore.kt`: "Don't provide tes stuff in prod code"
- Triage: adjustment (screenshot name; screenshot criterion now names the format) · rule + adjustment (test double in a
  published module).
- Route: implementer (both).
- Changed: `LBFeedbackAttachment.screenshotName(clock)` builds `screenshot-yyyyMMdd-HHmmss.jpg` (UTC) once, used by
  Android capture, iOS capture and the ui codec (gallery pick); redaction keeps the incoming name.
  `InMemoryLBFeedbackStore` left feedback-core and is now `FakeLBFeedbackStore` in the unpublished `feedback-test`;
  README, CHANGELOG and wiki no longer list it. New `LBFeedbackAttachmentTest` (name format).
- Rule captured: `testing.md` → "Fakes over mocks": test doubles never ship in a production module's main source set —
  test sources, or a dedicated `<module>-test` module (published or not).
  Same rule proposed upstream in ai-guidelines (`lunabee-engineering` baseline).
- Build: 3 modules host tests, core/ui iosArm64 + ui iosSimulatorArm64, `:demo-compose:assembleDebug`, detekt ✅.
  WARNINGS: none new.
- Review: deferred (`review.when: on_validation`). Verify: round 3: manual validation — not run by agent.

## Implementation

- **Brick 1 — feedback-core** ✅ build (android + iosArm64 + iosSimulatorArm64) · 5 store tests (`:feedback-core:testAndroidHostTest`) · detekt ✅
  - `settings.gradle.kts`, `AndroidConfig.kt` (`FEEDBACK_CORE_VERSION = "1.0.0"`), BOM constraint (`addModule`, no jvm target)
  - `model/` (`LBFeedbackDraft`, `LBFeedbackType`, `LBFeedbackItem`, `LBFeedbackStatus`, `LBFeedbackAttachment`, `LBFeedbackMetadata`, `LBFeedbackRemoteState`), `config/LBFeedbackConfig`, `transport/` (`LBFeedbackTransport`, `LBFeedbackError` thrown, like `LBParseException`), `store/` (`LBFeedbackStore`, DataStore JSON list + reporter key; `Context.lbFeedbackStore()` / iOS `lbFeedbackStore()`), `metadata/` (`LBFeedbackMetadataProvider`, expect/actual platform info)
  - `send` creates the item only; `attach` is the separate retryable upload; `update` takes full draft + original metadata
  - WARNINGS: K/N "Cannot infer a bundle ID" (shared framework convention, pre-existing kind)
- **Brick 2 — feedback-plane** ✅ build · 13 tests (5 description, 8 transport, `ktor-client-mock`) · detekt ✅ · BOM `PLATFORM_VERSION` → `4.20.0`
  - `transport/LBPlaneFeedbackTransport` (depends on `ktor-client-core` only, consumer passes `HttpClient`, no retries), `description/LBPlaneDescriptionBuilder` (public object, escaped body, reporter, metadata table)
  - Plane v1.5.0 source checked (`intake.py`, `issue.py`, serializers): create returns intake row, `issue` = work item id used for GET/PATCH/DELETE + attachments; status int + `issue_detail.name`; attachment POST → `{upload_data, asset_id, …}`. Unverified: `upload_data` S3 `{url, fields}` shape, PATCH `is_uploaded` body, presigned URL reachable from device
  - Errors: 401/403 Unauthorized · 404 NotFound · 413 TooLarge · 429 RateLimited · other non-2xx `Server(code)` · `kotlinx.io.IOException` Network; >5 MB rejected before any call
  - WARNINGS: SKIE `Ktor_httpHttpStatusCode.description renamed` (from `api(ktor-client-core)`, same as parse-ktor)
- **Brick 3 — feedback-ui common** ✅ build (android + iosSimulatorArm64) · 11 tests (5 form VM, 6 list VM) · detekt ✅
  - `LBFeedback.install(config, transport, store, themeWrapper)` builds `LBFeedbackGraph` by hand; pending screenshot = in-memory `MutableStateFlow<LBFeedbackAttachment?>` (JPEG bytes); `setScreenshot(ImageBitmap | ByteArray)`, `clearScreenshot()`, `Themed {}`
  - Public composables `LBFeedbackFormScreen`, `LBFeedbackListScreen`, `LBFeedbackRedactScreen`; internal ViewModels; `LBFeedbackTestTags`; EN + FR strings (41)
  - Form phases Editing / Sending / Failed / ScreenshotFailed(remoteId) / Done; screenshot switch off by default; edit mode = `update` with stored metadata, no screenshot
  - List refreshes status via `get` on open; edit/delete Pending only; NotFound on delete removes locally
  - Redact in common Compose (boxes as 0..1 fractions, flattened, JPEG re-encode < 4.5 MB); `expect encodeJpeg` filled Android + iOS
  - NOTES: encode/decode on caller (main) thread — detekt `DispatcherSwitchOutsideDataLayer`; no `@Preview` (no tooling in catalog)
  - WARNINGS: 5 × K/N `KLIB loader: same unique_name … found in more than one library` (annotation, collection, navigationevent-compose, savedstate via compose/lifecycle deps) on `compileTestKotlinIosSimulatorArm64`
- **Brick 4 — feedback-ui platforms** ✅ build (android + iosArm64 + iosSimulatorArm64) · 11 tests unchanged · detekt ✅
  - Common: `LBFeedbackHost` (state back stack: form, redact, list, edit:<id>), expect `LBFeedbackBackHandler`, expect `rememberLBFeedbackImagePicker` (null on iOS), form `onListClick` + "Pick from gallery"
  - Android: `LBFeedback.install(application, …)` + `ActivityLifecycleCallbacks` tracker (WeakReference, ignores `LBFeedbackActivity`), `LBFeedback.open(context, startOnList)` (`drawToBitmap`, fallback no screenshot), `LBFeedbackActivity` (`FLAG_SECURE` before `setContent`, not exported), `LBFeedbackNotification.show/hide` (own channel, IMMUTABLE PendingIntent with "capture" extra, silent when `POST_NOTIFICATIONS` missing), `LBFeedbackShakeTrigger.enable/disable` (`ProcessLifecycleOwner`, 2.7 g, 1 s debounce), `PickVisualMedia` picker
  - iOS: `LBFeedbackController.get(startOnList, onClose)` + `captureScreen()` (key window, `UIGraphicsImageRenderer`), object like monitoring-ui (Swift can't see default args, detekt `FunctionNaming`)
  - NOTES: notification tap while app in background → tracked activity may be stopped, capture blank or skipped; iOS has no back gesture (top-bar Back only); `./gradlew detekt` exit 0 even with `e:` lines — output read
- **Brick 5 — demo, docs, release files** ✅ `:demo-compose:assembleDebug` · tests core 5 / plane 13 / ui 11 · iosArm64 + iosSimulatorArm64 · detekt ✅
  - Redact fix: image in `weight(1f)` box with `aspectRatio`, Undo/Done always below (device: y=2256 of 2424 px). Form already scrolls
  - `LBFeedback.install(config, transport, store)` overload for Swift (no default on `themeWrapper` variant)
  - demo-compose `demo/feedback/`: `FakeFeedbackTransport` (600 ms, network error switch, triage cycle), `FeedbackDemoScreen`, `FlagSecureActivity`, `LBFeedbackDemo.install` (Plane transport when `feedback.plane.token/baseUrl/workspace/projectId` set in gradle or `local.properties`)
  - `common/feedback/README.md` (Plane bot setup, security caveats), root README table, `CHANGELOG.MD` 2026-11-19 block
  - Library version const stays hand-synced "1.0.0" (KDoc points to `AndroidConfig.FEEDBACK_CORE_VERSION`)
  - Device notes (not proof, `emulator-5564` via berth): capture from FLAG_SECURE screen ✅; Send tap after typing title did not leave form (keyboard likely covered button) — unconfirmed
- **Brick 6 — feedback round 1** ✅ tests core / plane / ui · iosArm64 + iosSimulatorArm64 · demo assembleDebug · detekt ✅
  - Headless `client/LBFeedbackClient` (send → `LBFeedbackSendResult.Sent | PartiallySent`, `retryAttachments`, `items`, `refreshStatuses`, `update`, `delete`, `canEdit`, reporter name); ViewModels delegate to it; `LBFeedback.client`
  - `LBFeedbackDraft.attachments: List<LBFeedbackAttachment>`, `LBFeedbackAttachment.MaxBytes`; uploads sequential, stop at first failure, rest returned as failed
  - `LBFeedback.addAttachment / removeAttachment / clearAttachments` (internal `LBFeedbackPending`: screenshot slot + files); form Attachments section (name · size, Remove); phase `AttachmentsFailed(remoteId, failed, error)`
  - `LBFeedbackError.AttachmentRejected` (Plane 400 on attach step 1); MIME list from Plane v1.5.0 `settings/common.py` in KDoc + README
  - `LBFeedback.captureScreen(): LBFeedbackAttachment?` (Android), `LBFeedbackController.captureScreen()` / `captureScreenAsPending()` (iOS)
  - Mockup read as layout: existing UI already matched; only new Attachments section added
  - Device notes (not proof): attach sample file → listed; Send closes form (brick 5 issue = keyboard); list Pending → Edit → Save → Delete OK; no crash
  - NOTES: `update` racing a status change surfaces as `Failed(NotFound)` ("no longer exists"), wording slightly off
- **Autofix** (3 rounds + 1 micro): see `## Review`. New since brick 6: `LBFeedbackError.NotEditable`, `LBFeedbackStore.update(remoteId) { }` (atomic), `LBFeedbackMetadataProvider` fun interface + `LBFeedbackDefaultMetadataProvider`, `LBFeedbackScreenCapture` in feedback-core (Android tracker + `LBFeedbackCaptureExcluded`, iOS key window), `InMemoryLBFeedbackStore`, `PlaneJson`, `LBFeedbackImageDataSource` / `LBFeedbackUriDataSource` / `LBFeedbackBitmapDataSource` (injected dispatchers), sealed `LBFeedbackRoute` + `LBFeedbackHostViewModel` (per-entry `ViewModelStoreOwner`), `generateFeedbackVersion` Gradle task, unpublished `common/feedback/feedback-test` (shared fakes), iOS controller `modalInPresentation = true`

## Verification

- Build + tests (by PM, final): `./gradlew detekt` clean · host tests core 21 / plane 16 / ui 15 green · core/plane/ui `compileKotlinIosArm64` + ui `compileKotlinIosSimulatorArm64` · `:demo-compose:assembleDebug`
- Runtime check: `verify.mode: off` — not owed. Implementer sanity drives on emulator (berth), not proof: capture from a FLAG_SECURE screen ✅, redact Undo/Done visible ✅, attach sample file → listed ✅, send → list Pending → edit → save → edit again → delete ✅, shake enable/disable ✅, Send reachable with keyboard open (by scrolling) ✅
- Not run: iOS on simulator/device, Plane transport against the real server (no token), notification tap from background

## Review

### validation (autopilot) — 2026-11-19

Lenses: main style ✓ elegance ✓ structure ✓ correctness ✓ · focus concurrency ✓ · focus recomposition ✓. Tools: detekt clean.

- Round 1: 33 findings (3 🔴 · 9 🟠 · 21 🟡). 32 fixed; S4 half (store fakes merged, transport fakes rejected).
  - 🔴 send cancelled when leaving the form → back blocked while Sending · 🔴 shake listener leak → unregister in `disable()` · 🔴 delete crash on triaged item → `NotEditable`
  - 🟠 transport shape errors escape `LBFeedbackError` → `PlaneJson` · lost update refresh vs edit → atomic `store.update { }` · image IO/encode on main → data sources with injected dispatchers · redact VM/edit VM survived pop → per-entry stores · edit Back wiped pending files → `leave()` · list rows recomposed on every emission → stable row fields
- Round 2: 9 findings (1 🟠 · 8 🟡), all fixed: iOS swipe-down cancelled send → modal in presentation; Android undecodable pick NPE → `decodeImage` returns null; S4 transport part (verifier's new evidence: copies identical, unpublished module = no artifact) → `feedback-test`; redact apply + picker survive rotation; injected dispatcher in capture encode; README/KDoc `NotEditable`; `load()` early returns
- Round 3: concurrency clean, recomposition clean, main 1 🟡 (KVC string for `modalInPresentation`) → typed property, fixed past the cap (mechanical, compile-checked)
- Withdrawn: 0 · disputed: 0 · open: 0 → **verdict clean**
