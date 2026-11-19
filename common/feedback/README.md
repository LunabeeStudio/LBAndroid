# Feedback libraries

In-app feedback for Android and iOS. A tester writes a bug, an idea or a remark, optionally attaches a redacted
screenshot, and the feedback lands in the Intake queue of a [Plane](https://plane.so) project. "My feedback" lists what
was sent from this install with its live triage status.

| Module            | Content                                                                                       |
|-------------------|-----------------------------------------------------------------------------------------------|
| `feedback-core`   | Models, `LBFeedbackConfig`, `LBFeedbackTransport` contract, `LBFeedbackStore`, `LBFeedbackClient` (headless API), metadata |
| `feedback-plane`  | `LBPlaneFeedbackTransport`, the Plane Intake implementation of the contract                    |
| `feedback-ui`     | Compose Multiplatform screens, `LBFeedback` entry point, Android and iOS triggers              |

`feedback-core` knows neither Plane nor Ktor. `feedback-ui` does not depend on `feedback-plane`: the app picks the
transport. There is no dependency injection framework, `LBFeedback.install` wires everything by hand.

## Setup

```kotlin
implementation(platform("studio.lunabee:bom:<version>"))
implementation("studio.lunabee.feedback:feedback-ui")
implementation("studio.lunabee.feedback:feedback-plane")
androidMain.dependencies { implementation("io.ktor:ktor-client-android:<ktor>") } // or ktor-client-okhttp
iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:<ktor>") }
```

No HTTP engine is bundled: pass your own `HttpClient`. Do not install automatic retries on POST requests, it would
duplicate items.

## Android

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LBFeedback.install(
            application = this,
            config = LBFeedbackConfig(
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                buildVariant = BuildConfig.BUILD_TYPE,
                extras = { mapOf("route" to currentRoute()) }, // optional, never user data
            ),
            transport = LBPlaneFeedbackTransport(
                baseUrl = "https://plane.example.com",
                workspaceSlug = "my-workspace",
                projectId = "<project uuid>",
                token = { BuildConfig.FEEDBACK_TOKEN },
                httpClient = HttpClient(Android),
            ),
            store = lbFeedbackStore(),
            themeWrapper = { content -> MyAppTheme(content) }, // optional, replaces LBFeedbackTheme in LBFeedbackActivity
        )
    }
}
```

- `LBFeedback.open(context)` captures the screen on top (`View.drawToBitmap`, it works on `FLAG_SECURE` windows because it
  draws the app's own views, but misses `SurfaceView`, video and separate windows such as dialogs), then starts
  `LBFeedbackActivity`. `LBFeedback.open(context, startOnList = true)` opens "My feedback".
- `LBFeedbackActivity` is declared in the library manifest and sets `FLAG_SECURE`, because it shows a capture of the app.
- To embed the screens in your own navigation, use `LBFeedbackFormScreen`, `LBFeedbackListScreen` and
  `LBFeedbackRedactScreen`. They inherit your `MaterialTheme`. `LBFeedbackTestTags` holds the test tags.
- The screenshot is off by default. The form shows a switch when a capture exists, a preview, a redact action
  (black boxes) and a remove action. The photo picker (`PickVisualMedia`, no permission) lets the user pick an image
  instead.

### Screenshot and gallery

`LBFeedback.setScreenshot(bitmap)` and `LBFeedback.setScreenshot(encodedBytes)` are `suspend`: the decoding and the JPEG
encoding run off the main thread. The second returns `false` when the bytes are not an image.

### Attachments

An app can attach any file to the next feedback, before `open()` or while the form is open:

```kotlin
LBFeedback.addAttachment(LBFeedbackAttachment(bytes = log, name = "app.log", mimeType = "text/plain"))
LBFeedback.removeAttachment("app.log")
LBFeedback.clearAttachments()
```

- The form lists the files (name and size), the user can remove each one. A file added by the app is sent unless the user
  removes it. The screenshot keeps its own switch, off by default.
- Each file is at most 5 MB (`LBFeedbackAttachment.MaxBytes`), otherwise `LBFeedbackError.TooLarge` before any call. Each
  file is one attachment on the remote item, uploaded one after the other.
- If the item is created but an upload fails, the item is kept and "Retry attachments" uploads only the failed files.
- Plane only accepts the MIME types of its `ATTACHMENT_MIME_TYPES` setting (see the KDoc of `LBPlaneFeedbackTransport`). Another
  type makes the upload fail with `LBFeedbackError.AttachmentRejected`. Use `text/plain` for logs.
- Files stay in memory, are cleared once the feedback is sent, and are **never written to disk**.
- **Attached files go to Plane as they are: they must never carry user data.**
- `LBFeedbackScreenCapture.capture()` (`feedback-core`, no Compose needed) returns a capture of the current screen as a
  JPEG `LBFeedbackAttachment`, without opening the Activity. On Android, `LBFeedbackScreenCapture.install(application)` must
  have been called (`LBFeedback.install(application, ...)` does it). Pass the result to `addAttachment` or to your own
  draft. Only the drawing runs on the main thread, the encoding does not.

### Opt-in triggers

- `LBFeedbackNotification.show(context)` / `hide(context)`: an ongoing notification whose tap opens the form with a capture.
  The library never asks for `POST_NOTIFICATIONS`: on API 33 and above the app must declare it and hold it, otherwise
  nothing is shown.
- `LBFeedbackShakeTrigger.enable(application)` / `disable()`: opens the form when the device is shaken, while the app is in
  the foreground. Do not enable it in an app that already uses shake for something else.

Both are off unless the app calls them.

## iOS

```swift
LBFeedback.shared.install(config: config, transport: transport, store: LBFeedbackStoreProvider_iosKt.lbFeedbackStore())

await LBFeedbackController.shared.captureScreenAsPending() // optional: screenshot of the key window
// LBFeedbackScreenCapture.shared.capture() returns the capture as an LBFeedbackAttachment instead
let controller = LBFeedbackController.shared.get(startOnList: false, onClose: { dismiss() })
present(controller, animated: true)
```

The controller is modal in presentation: a swipe down does not dismiss it, so a feedback being sent is never cut off. Dismiss it in `onClose`.

iOS v1 has no gallery picker, shake trigger or notification.

## Theming

The screens read `MaterialTheme`, so they follow your app when you embed them. The ones the library hosts itself
(`LBFeedbackActivity`, `LBFeedbackController`) use `LBFeedbackTheme` by default: Material 3 light or dark colors following
the system mode. On Android the activity is edge-to-edge with auto light or dark system bar icons, and its window
background matches the mode before Compose draws. Pass a `themeWrapper` to `LBFeedback.install` to use your own theme
instead.

## Headless API

To build your own UI, use `LBFeedbackClient` from `feedback-core`, no Compose needed. It holds the business rules, and the
built-in screens use it too. With `feedback-ui`, `LBFeedback.client` is the one built by `install`. Without it, build it:

```kotlin
val client = LBFeedbackClient(config = config, transport = transport, store = lbFeedbackStore())
// or LBFeedbackClient(transport, store, metadataProvider) with your own LBFeedbackMetadataProvider

val result = client.send(
    LBFeedbackDraft(
        type = LBFeedbackType.Bug,
        title = "Crash on start",
        body = "Steps...",
        reporter = "Ada",
        attachments = listOf(LBFeedbackAttachment(bytes, "app.log", "text/plain")),
    ),
)
when (result) {
    is LBFeedbackSendResult.Sent -> show(result.item)
    is LBFeedbackSendResult.PartiallySent -> {
        // the item exists: retry only the files that failed
        client.retryAttachments(result.item.remoteId, result.failedAttachments)
    }
}
```

| Member                                       | Role                                                                                   |
|----------------------------------------------|----------------------------------------------------------------------------------------|
| `send(draft)`                                | creates the item, remembers it, uploads the attachments one by one                      |
| `retryAttachments(remoteId, attachments)`    | uploads only the given files to an existing item                                        |
| `items`                                      | `Flow` of the sent items, newest first, with last known status                          |
| `refreshStatuses()`                          | reads the live status of every item, keeps the last known one on failure                |
| `update(remoteId, draft)` / `delete(remoteId)` | pending items only (`canEdit(item)`, else `LBFeedbackError.NotEditable`), `delete` also removes locally when remote says NotFound |
| `reporterName` / `setReporterName(name)`     | name remembered for the next feedback                                                   |

`LBFeedbackMetadataProvider` is a `fun interface` you can replace (`LBFeedbackDefaultMetadataProvider` is the standard one).

Swift (with `feedback-ui` or the core framework):

```swift
let client = LBFeedback.shared.client
try await client.send(draft: draft)          // LBFeedbackSendResult
try await client.refreshStatuses()
```

## LBFeedbackTransport

```kotlin
interface LBFeedbackTransport {
    suspend fun send(draft: LBFeedbackDraft, metadata: LBFeedbackMetadata): String
    suspend fun get(remoteId: String): LBFeedbackRemoteState
    suspend fun update(remoteId: String, draft: LBFeedbackDraft, metadata: LBFeedbackMetadata)
    suspend fun delete(remoteId: String)
    suspend fun attach(remoteId: String, attachment: LBFeedbackAttachment)
}
```

Every function throws an `LBFeedbackError` (`Unauthorized`, `RateLimited`, `Network`, `TooLarge`, `Server`,
`AttachmentRejected`, `NotFound`). `LBFeedbackClient` can also throw `NotEditable`. `send` creates the item only, `attach` uploads one file in a separate step so a failed
file can be retried alone. This is the seam
for a relay server: implement the same interface in another module and nothing else changes.

On failure the draft, files included, stays in memory and the user taps Retry. If the item was created but some uploads
failed, the item is kept and the form offers "Retry attachments".

## Plane bot setup

1. Create a dedicated Plane user for the app (the bot).
2. Add it to the target project only, with the **Guest** role.
3. Keep `guest_view_all_features` **off**, so the bot only sees its own items.
4. Enable **Intake** on the project.
5. Create a long-lived API token for the bot and give it to the app build, outside version control.

A Guest can create intake items, edit the name and description of its own items, delete them and upload attachments to
them. The library lets the user edit and delete only while an item is pending, so a tester cannot overwrite the triage
edits of the product owner.

Plane API facts to know:

- All testers share one token: the server cannot tell them apart. "My feedback" is filtered locally, from the store.
- Uploads are limited to 5 MB. The library re-encodes screenshots to stay below it.
- Each attachment is 3 requests. The API accepts about 60 requests per minute per key. Above it, Plane answers 429 (`RateLimited`).
- The presigned upload URL must be reachable from the device. On a self-hosted instance it must point to a public host.

## Security caveats

- The token ships inside the app. Use the library in dev and beta builds only, never in a store build.
- If the token leaks, rotate it by hand in Plane. Anyone holding the APK can post to the intake.
- A screenshot can show sensitive screens. It is off by default, previewed before sending, and redactable. It stays in
  memory.
- `LBFeedbackConfig.extras` must never carry user data.
- Nothing is written to disk except the store (sent items and the reporter name).
- The library has no log API and attaches no logs.
