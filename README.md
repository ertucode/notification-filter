# QuietFilter

A small, local-only Android notification blocker. Rules can target one app or all apps, combine multiple text conditions, and use contains, exact, negative, or regular-expression matching. Removed notifications remain in a 30-day in-app history. Standalone six-digit codes in incoming notifications appear in a small right-center pop-up with a copy button and the SMS or email sender when available. The app requests display-over-other-apps access for this placement and falls back to a heads-up notification without it.

Rule configurations can be imported, saved, or shared as `.quietfilterconfig` JSON files from **Rules → Transfer**. Opening or sharing one of these files to QuietFilter starts the import confirmation directly, including from WhatsApp. History is never included in exported configurations.

## Build (isolated with Docker)

Start Docker Desktop, then run:

```sh
docker compose build
docker compose run --rm android-build
```

The container is pinned to `linux/amd64` because Android's bundled AAPT2 tool requires it; Docker Desktop handles emulation on Apple Silicon.

Installable APKs will be at `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk` when a local signing key is configured.

## Stable local signing

Both build types use `.signing/quietfilter-local.p12` when present. Keep this file safe:
Android preserves app data during APK updates only when the package name and signing key
remain unchanged. The `.signing` directory is excluded from version control and should
never be used as a Play Store production key.

## Use

1. Install and open the app.
2. Tap **Grant access** and enable QuietFilter under Notification access.
3. Add a rule. Leave **App** set to **All apps** for app-independent matching.

Matching happens only when Android delivers a notification to the listener. Processing and history storage stay on the device. A notification may appear briefly before Android removes it.
