---
issue: LBA-3
phase: grilled
type: feature
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
      `is_uploaded`).
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
- [ ] `./gradlew detekt` and `./gradlew test` pass.

## Risks to verify while coding

- The presigned upload URL must be reachable from a device. On self-hosted CE it should point to
  `projects.lunabee.ovh/uploads`.
- `guest_cannot_view_issue` must let the bot read its own intake items when `guest_view_all_features` is off.
- Capturing the previous activity when the notification opens the form: it is paused, but its decor view is still
  attached.
