# Android app

The bylarik app itself. See the [main README](../README.md) for what it does and how to install it.

- `app/` is the app (Kotlin, Jetpack Compose).
- `ci/smoke.py` is the emulator test that GitHub Actions runs before every release.
- `release-notes.json` holds the "what's new" text the app shows in its update card, in Ukrainian, Russian and English. Update it with every version bump in `app/build.gradle.kts`.
