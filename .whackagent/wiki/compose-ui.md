# Compose UI

Android Jetpack Compose libs under `compose/` (published as `studio.lunabee.compose[.<sub>]:<module>`): text/image specs, theming, form fields, image crop, haptics, accessibility, Glance widgets, test utils.

Out of scope: [[presenter]] and `compose/navigation`. Non-UI building blocks (logger, loading, monitoring UI) in [[common-libraries]]; module/publishing mechanics in [[build-and-publishing]] and [[architecture]].

## Module map

| Module (Gradle path) | Plugin | Version const | Depends on |
|---|---|---|---|
| `:compose:core` | android-compose | `LBCCORE_VERSION` | `logger-kermit` |
| `:compose:foundation` | android-compose | `LBCFOUNDATION_VERSION` | — |
| `:compose:theme` | android-compose | `LBCTHEME_VERSION` | `material-color-utilities` |
| `:compose:material-color-utilities` | `java-library` (pure Java) | `MATERIAL_COLOR_UTILITIES_VERSION` | — |
| `:compose:image` | android-compose | `LBCIMAGE_VERSION` | `compose:core`, Coil (+ SVG) |
| `:compose:crop` | android-compose | `LBCHAPTIC_VERSION` (shares haptic's constant) | Coil, `zoomable`, exifinterface |
| `:compose:haptic` | android-compose | `LBCHAPTIC_VERSION` | — |
| `:compose:accessibility` | android-compose | `LBCACCESSIBILITY_VERSION` | — |
| `:compose:glance` | android-compose | `LBCGLANCE_VERSION` | `api` glance-appwidget |
| `:compose:uifield-core` | android-compose | `LBCUIFIELD_CORE_VERSION` | `compose:core`, `compose:image` |
| `:compose:uifield-countrypicker` / `-phonepicker` | android-compose | = `LBCUIFIELD_CORE_VERSION` | `uifield-core`, hbb20 CCP, (libphonenumber) |
| `:compose:uifield-form` | android-library (no Compose) | `LBCUIFIELD_FORM_VERSION` | `api` `uifield-core` |
| `:compose:androidtest` / `:compose:robolectrictest` | android-compose | `LBCANDROIDTEST_VERSION` / `LBCROBOLECTRICTEST_VERSION` | compose-ui-test / Robolectric |

All Android-only. Nested folders become group segments: `compose/uifield/uifield-core` → `studio.lunabee.compose.uifield:uifield-core`, `compose/test/androidtest` → `studio.lunabee.compose.test:androidtest`.

## core — UI value specs

Purpose: describe text and images as **data** so reducers/UiStates carry them without `Context`.

- `LbcTextSpec` (sealed, `@Stable`): `Raw`, `Annotated`, `AnnotatedBuilder`, `StringResource`, `PluralsResource`, `StringByNameResource` (resolved via `getIdentifier`). Exposes composable `string` / `annotated` and `string(resources)`; `LbcTextSpec` args resolved recursively. `compose/core/src/main/kotlin/studio/lunabee/compose/core/LbcTextSpec.kt`
  - `string(context)` deprecated for `string(resources)` with `LocalResources.current` (lint `LocalContextResourcesRead`).
  - `AnnotatedBuilder` works only in composition; `Resources` overloads are `DeprecationLevel.ERROR` and log error.
  - `Inlinable` + `LbcInlineTextContent` carry inline content; `null` placeholder lets consuming `Text` measure it (e.g. icon sized to text height).
- `LbcImageSpec` (sealed): `Icon` (drawable res + tint), `KtImageVector`, `ImageDrawable` (optional forced `uiMode`), `Bitmap`, `ByteArray`, `Url` / `Uri` (`allowCaching`, `fallback`). `compose/core/src/main/kotlin/studio/lunabee/compose/core/LbcImageSpec.kt`

## image

- `LbcImage(imageSpec, contentDescription: LbcTextSpec?, …)` renders any `LbcImageSpec`. `compose/image/src/main/kotlin/studio/lunabee/compose/image/LbcImage.kt`
  - Icons fall back to `LocalContentColor` when tint `Unspecified`.
  - Url/Uri go through Coil with SVG decoder; `allowCaching = false` disables memory **and** disk cache; on error `fallback` spec rendered.
  - `ImageDrawable.uiMode` resolves drawable through configuration context so right day/night variant picked; only set masks merged.
  - In preview (`LocalInspectionMode`) placeholder painter drawn since Coil skips request.

## foundation

- `StyledTextItem(rawBaseText, rawBaseTextStyle, textToHighLightList)` — highlights every occurrence of each `TextToHighlight` (tag, style, `ignoreCase`, optional click `action` attached as link annotation), keeps original casing. `compose/foundation/src/main/kotlin/studio/lunabee/compose/foundation/StyledTextItem.kt`

## Theming — theme + material-color-utilities

No `MaterialTheme` wrapper shipped; lib gives **Material 3 `ColorScheme` generated from one seed color**, pass to own `MaterialTheme`.

- `LbcThemeUtilities.getMaterialColorSchemeFromColor(color, isInDarkMode)` — builds HCT tonal-spot palettes, maps every M3 role (incl. `surfaceContainer*`, `surfaceBright/Dim`) into `ColorScheme` (variant `NEUTRAL`).
- `LbcThemeUtilities.getToneForColor(color, tone)` — tone in `0..100` (asserted).
- `material-color-utilities` = vendored copy of Google's Java color math (`Hct`, `Cam16`, `DynamicScheme`, `SchemeTonalSpot`, `MaterialDynamicColors`, …) in package `material` (`compose/material-color-utilities/src/main/java/material/`). Plain JVM lib, hence `java-library-conventions`.

## uifield — form fields

Purpose: stateful, self-validating form fields owned by ViewModel/presenter, rendered via `field.Composable(modifier)`.

- `UiField<Form, Display>` (`compose/uifield/uifield-core/src/main/kotlin/studio/lunabee/compose/foundation/uifield/UiField.kt`):
  - Value in `MutableStateFlow`, **persisted to `SavedStateHandle` under `id`** on every set, restored on creation.
  - `isFieldInError: (Form) -> UiFieldError?` drives `isInError` (flow) and `checkAndDisplayError()`.
  - Text fields validate **on focus loss, only after field focused once**; typing dismisses error.
  - `options: List<UiFieldOption>` render as trailing actions (password visibility, date/hour picker).
- Concrete fields: `NormalUiTextField`, `PasswordUiTextField`, `DateUiField`, `TimeUiField`, `DateAndHourUiField`, `BooleanUiField` base.
- Styling: `TextUiField` takes `UiFieldStyleData` strategy (`ComposeTextField(...)`); default `DefaultUiFieldStyleData` = M3 `OutlinedTextField`. Swap to apply app design system.
- `uifield-countrypicker`: `CountryPickerUiField` + searchable bottom sheet (`CountrySearchDelegate`), country data from hbb20 CountryCodePicker.
- `uifield-phonepicker`: `PhonePickerUiField` (`CountryCodeFieldData`), `PhoneNumberValidator` and `PhoneNumberVisualTransformation` on libphonenumber; error-message display left to client.
- `uifield-form`: `Form(fields)` — `observeFieldsValidity()` (all valid, distinct) and `checkAndDisplayErrors()` across fields.

## crop

- `LbCropView(state)` + `rememberLbCropViewState(originalBitmap, originalOrientation, finalImageMinSize)`. `compose/crop/src/main/kotlin/studio/lunabee/compose/crop/`
  - `initState()` applies EXIF orientation, writes image to cache temp file (`lb_crop_temp_file.jpg`) view works on; zero-size bitmap throws `LbCropNullImageThrowable`.
  - Zoom/pan via `net.engawapg.lib.zoomable`; `maxScale` capped so output never below `finalImageMinSize`; view re-fits after 300 ms delay.
  - `crop()` returns `Bitmap`; `rotate()` turns 90°; call `clear()` to delete temp file.

## haptic

- `LbcHapticEffect` sealed families: `Predefined` (API 29+), `FeedbackConstants` (View, API 30+), `CompositionPrimitives` (API 30/31+), `Compose` (`HapticFeedbackType`).
- `rememberLbcHapticFeedback()` → `LbcHapticFeedback.perform(effect, fallback = Compose.LongPress)`: effect unsupported on device/API → plays fallback; no vibrator → no-op. `getSupportedHapticEffect()` lists device capabilities. `compose/haptic/src/main/kotlin/studio/lunabee/compose/foundation/haptic/`

## accessibility

- `rememberAccessibilityState()` → `AccessibilityState.isAccessibilityEnabled` / `isTouchExplorationEnabled` (TalkBack), kept live by `AccessibilityManager` listeners. KDoc rule: avoid changing UI on these flags unless no other option.
- `LbcAccessibilityUtils.cleanForAccessibility()` strips chars outside letter/mark/number/punctuation/space classes (e.g. emoji) before reading.

## glance (widgets)

Workarounds for Glance limits (see `compose/glance/README.md`): `GlanceRoot` (applies `appWidgetBackground`), `GlanceBackground` (XML drawable behind content via RemoteViews), `GlanceTypefaceText` / `GlanceTypefaceButton` (custom fonts rendered **as bitmaps** — unsupported natively), `GlanceViewFlipper`, `cornerRadiusCompat` (rounded corners below API 31), `glanceStringResource` / `glancePluralResource`, `GlanceActions`, `PinWidgetToHomeScreenHelper` (pin from app, API 26+).

## test

- `androidtest`: `LbcComposeTest` (base class running `ComposeUiTest` on `ComponentActivity`; prints whole-screen capture on failure via `LbcPrintRule`), `SemanticsMatcher.waitUntil…` helpers, `printToCacheDir`, `LbcPrintRule` (screenshots to internal/public storage), `LbcSaverRule` (state-restoration testing), `LbcResourcesHelper` / `LbcFolderResource` (copy test resources to device).
- `robolectrictest`: `LbcInjectComponentActivityRule` registers `ComponentActivity` in Robolectric's shadow package manager so Compose tests launch it under Robolectric.

## Demo — `demo-compose`

One menu-driven app (`MainScreen` lists `MenuSection`s, routes in `demo-compose/src/main/kotlin/studio/lunabee/compose/navigation/Destinations.kt`), one package per lib under `.../compose/demo/`:

| Package | Shows |
|---|---|
| `theme/ThemeScreen.kt` | seed-color `ColorScheme` + tone ramp via `LbcThemeUtilities` |
| `foundation/TextScreen.kt`, `core/` | `StyledTextItem`, `LbcTextSpec` |
| `image/`, `crop/`, `haptic/` | `LbcImage`, `LbCropView`, haptic effect catalogue |
| `uifield/UiFieldsScreen.kt` | text/password/date/time/country/phone fields |
| `glance/` | `GlanceWidgetDemo` widget + pin helper |
| `monitoring/` | Ktor plugins + monitoring UI ([[common-libraries]]) |

`common/AppDemoTheme.kt` picks custom scheme, else Material You dynamic colors, else default light/dark. `demo-compose/src/androidTest` uses `compose:androidtest` (e.g. `PrintRuleDemoTest`). `compose:accessibility`, `uifield-form`, `robolectrictest` have no demo screen. Presenter and navigation demos: [[presenter]]; sync demo: [[synchronization]].