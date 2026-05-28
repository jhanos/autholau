# AGENTS.md

## Project structure

- `app/` — Android app (Kotlin, minSdk 26, compileSdk 34, AGP 8.3.2, Kotlin 2.0.21)
- `server/` — Python Flask backend (`server/app.py`, `server/storage.py`)
- Package: `com.autholau`, app name: Autholau

## Build

Only one Gradle version is installed. Always use the exact command below — no wrapper.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
/opt/gradle/gradle-8.4/bin/gradle assembleRelease -x lint \
  --project-dir /home/jhanos/git/perso/autholau \
  -Dhttp.proxyHost=172.23.194.209 -Dhttp.proxyPort=43051 \
  -Dhttps.proxyHost=172.23.194.209 -Dhttps.proxyPort=43051
```

- Keystore: `/home/jhanos/.android/jhanos-android.keystore`, alias `my-key-alias`, password `android`
- `android.useAndroidX=false`, `android.enableJetifier=false`, `isMinifyEnabled=false`, `isShrinkResources=false`
- AGP 8.5+ requires Gradle 8.7+ — not available; stay on 8.3.2.

## Hard constraints

- **Zero runtime Android dependencies** — use `HttpURLConnection` + `org.json`, `SharedPreferences`. No AndroidX, no third-party libs.
- Pure View-based UI (no Compose).
- All UI strings in French.
- Theme: `android.Theme.Material` dark — bg `#121212`, bar `#1E1E1E`, accent `#BB86FC`.

## Architecture

- `RouterActivity` → cold-start routing to `SetupActivity` / `LoginActivity` / `MainActivity`
- `MainActivity` owns all shopping + event UI. Single activity, section-switched via drawer.
- Sections: `EVENTS`, `LISTE`, `COURSE`, `LECLERC`, `GRAND_FRAIS`, `AUTRE`
- All network calls in `Thread {}`, results via `runOnUiThread {}`. No coroutines.
- `Api.kt` — static object, set `Api.baseUrl` and `Api.token` before use.
- `Prefs.kt` — all SharedPreferences keys and cache helpers.

## Shopping semantics (critical)

- **Liste**: merged Leclerc + Grand Frais rows with `[L]`/`[GF]`/`[L+GF]` badges. Tick = `planned=true`.
- **Course**: merged planned items from both stores. Tick = `planned=false, checked=false` + 1h grace period (same as store lists). Clear button is hidden — grace period handles cleanup.
- **Leclerc / Grand Frais**: tick = bought (`planned=false, checked=false`) + 1h grace period in-memory via `gracePeriodIds` + `graceHandler.postDelayed(3_600_000L)`. Untick within grace = restore `planned=true`.
- **Autre**: tick = `checked=true`; clear = permanent delete.
- Siblings = items with same `(name, category)` across Leclerc ↔ Grand Frais. Identified at render time from `ShoppingRow.Item.sibling`. Use the `sibling` captured at render time inside listeners — do not re-query the live list.
- `currentStore()` returns `""` for LISTE and COURSE sections.

## Recurring items

- `RecurringItem` fields: `id, name, category, stores, periodWeeks, lastBought, updatedAt`
- `updatedAt` is used for server-side conflict resolution — always set to `System.currentTimeMillis()` on any mutation.
- `lastBought` is preserved on period-only edits; only reset to `now` on first creation.
- `checkAndAddRecurring()` runs on `onResume` and `onCreate` — uses a **single background thread** to avoid race conditions on the `shopping` list.
- `Prefs.updateRecurringLastBought(ctx, id, ts)` looks up by `id`, not `(name, category)`.

## Server

- Base URL: `https://famille.thonis.fr`
- Auth: shared family password → JWT; pass as `Authorization: Bearer <token>`
- Routes: `/auth/login`, `/events`, `/shopping`, `/categories`, `/recurring`

## Common pitfalls

- `optString("field", null)` can return the literal string `"null"` on some Android versions. Always use `.takeIf { it.isNotEmpty() && it != "null" }`.
- `optJSONArray` / `optInt` instead of `getJSONArray` / `getInt` — the `get*` variants throw on missing keys.
- When adding a new field to a model that is persisted via `Prefs`, update both `save*` and `load*` in `Prefs.kt`, the `*ToJson` and `parse*` helpers in `Api.kt`, and the data class.
- Grace period state (`gracePeriodIds`, `graceRunnables`) is in-memory only — lost on app close. This is intentional.
