# Jellyx.tv

Native Android TV & Android Mobile client for Jellyfin, forked from [Findroid](https://github.com/jarnedemeulemeester/findroid) under [GPLv3](LICENSE) and tailored for resilient playback, Live TV zapping, concurrent-screen enforcement, offline mobile downloads, and self-hosted OTA updates.

## Dual Platform Builds (Single Repository)

This repository builds both targets from one shared core with distinct `applicationId`s so TV and Mobile builds never collide:

| Target | Module | Application ID |
| --- | --- | --- |
| **Android TV / Fire TV** | `:app:tv` | `tv.jellyx.app.tv` |
| **Android Mobile / Tablet** | `:app:phone` | `tv.jellyx.app.mobile` |

## Key Features & Enhancements

- **Smart Playback & Resilience (`player/local`)**:
  - `SmartSourceSelector` with device/network capability probes and RAM-aware `MPVPlayer` profiles.
  - Delayed neighbor-episode preloading (after `STATE_READY`), single-retry source refresh on transient HTTP errors, and safer ExoPlayer-to-MPV failover.
  - Bounded single-content session streaming cache (`cacheDir/jellyx_session_cache_v1`) that preserves blocks across accidental restarts of the same item and purges automatically when switching content.
- **3-Tier Segment Resolution & Ratings**:
  - Intro/outro/recap/commercial resolution via local DB, Jellyfin MediaSegments, IntroSkipper, and chapter heuristics, plus TV skip countdown.
  - Optional external ratings cache (IMDb/TMDb/Rotten Tomatoes/Metacritic) via Room + `RatingsRow` UI.
- **Live TV & EPG**:
  - Native Live TV channels, current/next EPG program metadata, channel switcher HUD, and HLS zapping reconnect logic.
- **Concurrent-Screen Enforcement & Session Revocation**:
  - Global authenticated WebSocket listener (`SessionEventManager`) with `postCapabilities()` (`DISPLAY_MESSAGE`), keyword + `STOP` enforcement, non-bypassable amber warning modal (`ConcurrentScreenAlertDialog`), and automatic local logout on HTTP `401`/token revocation.
- **Private Background Offline Downloads (Mobile)**:
  - `WorkManager` foreground service (`MediaDownloadWorker`) streaming approved video + external subtitles directly into app-private `noBackupFilesDir/offline_cache/` so downloads survive screen lock without exposing media to public storage.
  - Mobile in-player episode drawer (`VodEpisodesDrawer`) matching TV's episode switcher.
- **24-Hour Auto-Updater**:
  - Background `UpdateCheckWorker` checking either a custom HTTPS `version.json` manifest or GitHub Releases (`kevinaxelerazo/jellyx.tv`), matching platform APKs (`tv` vs `mobile`/`phone`), verifying optional `.sha256` sidecars, and launching `FileProvider` install prompts.

## Configuration (`local.properties`)

Public repository builds ship **with no hardcoded server URL or API keys**. Copy `local.properties.example` to `local.properties` (gitignored) to customize local builds:

- `JELLYX_DEFAULT_SERVER_URL` / `JELLYX_FALLBACK_SERVER_URL`: Leave empty for generic builds where users enter their own Jellyfin server URL.
- `JELLYX_UPDATE_REPO`: GitHub repository slug for 24h auto-updates (defaults to `kevinaxelerazo/jellyx.tv`).
- `JELLYX_UPDATE_MANIFEST_URL`: Optional custom HTTPS JSON manifest URL taking precedence over GitHub Releases.
- `JELLYX_TMDB_API_KEY`, `JELLYX_OMDB_API_KEY`, `JELLYX_MDBLIST_API_KEY`: Optional ratings provider keys.

## Building & Testing

Run unit tests across core modules:

```bash
./gradlew :data:testDebugUnitTest :setup:testDebugUnitTest :core:testLibreDebugUnitTest :player:local:testDebugUnitTest --console=plain
```

Build release APKs:

```bash
./gradlew :app:tv:assembleLibreRelease :app:phone:assembleLibreRelease --console=plain
```

## License & Upstream Credits

Licensed under the [GNU General Public License v3.0](LICENSE). Based on [Findroid](https://github.com/jarnedemeulemeester/findroid) by Jarne Demeulemeester and contributors.
