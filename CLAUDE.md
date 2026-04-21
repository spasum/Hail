# Hail — Developer Notes

## Project
Android app freezer (Shizuku-based). Package: `io.spasum.hailshizuku`.

## Critical: Home Screen Shortcut Rules

### setActivity() MUST point to MainActivity
**Never use ApiActivity as the `setActivity()` target in ShortcutInfoCompat.**

```kotlin
// WRONG — causes "Ошибка добавления" on Vivo OriginOS / BBK launchers:
.setActivity(ComponentName(BuildConfig.APPLICATION_ID, API_ACTIVITY_CLASS))

// CORRECT:
.setActivity(ComponentName(BuildConfig.APPLICATION_ID, MAIN_ACTIVITY_CLASS))
```

**Why:** Vivo/BBK/OriginOS launchers validate the `setActivity()` component at pin time and
reject shortcuts bound to activities with `android:excludeFromRecents="true"` or
`android:launchMode="singleInstance"`. `ApiActivity` has both. `MainActivity` (the LAUNCHER
activity) passes validation on all launchers.

The shortcut *intent* still targets `ApiActivity` explicitly — runtime behaviour is unchanged.

### Shortcut intent must NOT have CATEGORY_DEFAULT
For explicit-component intents, `CATEGORY_DEFAULT` is unnecessary and Vivo launchers reject
shortcut intents that carry non-standard categories during pin-time validation.

### Always send legacy broadcast alongside modern API
`ShortcutManagerCompat.requestPinShortcut()` returns `true` on Vivo/OriginOS even when the
launcher silently discards the shortcut. Always also send the legacy `INSTALL_SHORTCUT`
broadcast unconditionally — AOSP launchers (Android 8+) ignore it (no duplicates), OEM
launchers process it.

### Dynamic icon updates
Pinned shortcut icons update automatically via `ShortcutManagerCompat.updateShortcuts()` when
the app's frozen state changes. This is called from `AppManager.setAppFrozen()`.

**This only works for shortcuts created via the modern API** (registered in ShortcutManager).
Shortcuts created via legacy broadcast are invisible to ShortcutManager and their icons cannot
be updated programmatically.

## Architecture
- `HShortcuts.kt` — all shortcut creation/update logic
- `AppManager.kt` — freeze/unfreeze; calls `HShortcuts.updateShortcutIcon` on state change
- `ApiActivity.kt` — handles shortcut tap intents; `singleInstance`, `excludeFromRecents`
- `MainActivity.kt` — launcher activity; must be the `setActivity()` target for shortcuts
