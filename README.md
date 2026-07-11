# Dojoy

A small self-hosted reward-points app for families — tap a behavior badge to give (or take back) a point, with a shared board that stays in sync across every phone and tablet on the home network, plus a native Android TV app so the board can live on the living-room TV.

## Features

- **Multiple profiles** — add or remove a profile for each kid at any time, with a photo or a colored avatar.
- **Point categories** — a set of default behaviors (listening, helping others, teamwork, tidying up, reading, and more), each with its own little illustrated icon and a running +/− counter. Counts never go below zero.
- **Shared state** — one small Node server holds the data, so everyone's phone/tablet/TV shows the same numbers in near real time.
- **Android TV app** — a lightweight WebView wrapper that shows the board full-screen on a TV, with sensible remote-control back-button behavior and self-updates when a new build is deployed.
- No accounts, no cloud dependency — everything runs on your own LAN.

## Project layout

```
server/       Node.js server (zero npm dependencies) + the web frontend
  server.js       HTTP API + static file serving, JSON file storage
  public/         The frontend (single-page app)
  Dockerfile
  docker-compose.yml

android-tv/   Android TV app (Kotlin) — a WebView pointed at the server
  app/src/main/java/com/dojoy/tv/MainActivity.kt
  app/src/main/java/com/dojoy/tv/Updater.kt   self-update check on launch
```

## Running the server

```
cd server
docker compose up -d --build
```

By default it listens on port `8080` inside the container (mapped to `8090` on the host in `docker-compose.yml`). Data is stored as a single JSON file in a `data/` volume next to the compose file.

Edit the `DOJOY_URL` constant in `android-tv/app/src/main/java/com/dojoy/tv/MainActivity.kt` (and the version-check URL in `Updater.kt`) to point at your own server's address before building the Android app.

## Building the Android TV app

Requires the Android SDK and a JDK 17.

```
cd android-tv
./gradlew assembleDebug
```

The resulting APK can be sideloaded onto most Android TV / Google TV boxes using an app like Downloader. Once installed, later versions are picked up automatically: the app checks a small `dojoy-tv-version.json` file on the server at launch and self-installs any newer build.

## License

Personal project, shared as-is with no warranty.
