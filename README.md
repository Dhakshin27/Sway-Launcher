# SwayLauncher

A custom Android Home launcher built with Kotlin and Jetpack Compose. SwayLauncher provides a focused, card-based launcher experience with installed-app discovery, app usage ranking, a personal dashboard, system shortcuts, local personalization, weather, steps, UPI quick access, and media controls.

## Features

- Registers as an Android Home app and asks to become the default launcher on Android 10+.
- Three vertically swipeable primary sections:
  - **Your Space** — clock, date, battery level, optional local wallpaper, clock/calendar shortcuts, and a personal dashboard.
  - **Your Apps** — installed-app grid with search, paging, launch-count ranking, cached icons, and Lawnicons support.
  - **Your System** — palette selector, storage information, and shortcuts to common Android settings.
- Five selectable visual palettes: Quiet, Crimson, Violet, Ember, and Sage.
- Personal dashboard featuring:
  - Weather for a chosen city, powered by Open-Meteo.
  - Daily step tracking through the device step counter.
  - Up to three speed-dial contacts.
  - A configurable UPI/payment-app quick launcher, including QR-scanner shortcuts when exposed by the payment app.
  - Active-media artwork, track details, and playback controls.
- Local persistence for app metadata/icons, launch counts, palette choice, dashboard settings, contacts, weather cache, step goal, selected payment app, and wallpaper URI.
- Lawnicons SVG icon pack integration, with Android app icons as fallbacks.

## Technology

- Kotlin 2.2.10
- Jetpack Compose with Material 3
- Android Gradle Plugin 9.5.0
- Gradle 9.6.0
- Android SDK:
  - `minSdk`: 26
  - `targetSdk`: 37
  - `compileSdk`: 37
- Java 11 compatibility
- SQLite via `SQLiteOpenHelper`
- Kotlin coroutines and `StateFlow`
- AndroidSVG for Lawnicons SVG rendering

## Getting Started

1. Open the project folder in Android Studio.
2. Allow Gradle to finish syncing dependencies.
3. Select an Android device or emulator running Android 8.0 (API 26) or newer.
4. Run the `app` configuration.
5. When Android prompts for a Home app, select **SwayLauncher** and choose it as the default launcher if desired.

You can return to the system launcher at any time from Android’s Home-app settings.

## Permissions and Access

SwayLauncher requests or uses the following capabilities:

| Capability                   | Purpose                                                                                           |
| ---------------------------- | ------------------------------------------------------------------------------------------------- |
| Home-app role                | Lets SwayLauncher operate as the device launcher.                                                   |
| Installed-app visibility     | Finds and launches apps that expose launcher activities.                                          |
| Notification listener access | Enables active media-session discovery and playback controls.                                     |
| Activity recognition         | Reads the step-counter sensor for the daily steps card.                                           |
| Location                     | Used for weather when device-location support is available; a city can also be selected manually. |
| Internet                     | Retrieves weather data and city search results from Open-Meteo.                                   |
| Contacts picker              | Lets the user choose speed-dial contacts without requesting broad contacts permission.            |
| UPI intent discovery         | Finds installed apps that handle `upi://` payment intents.                                        |

## Project Structure

```text
app/
├── src/main/java/com/me/mylauncher/
│   ├── MainActivity.kt                 # Entry point and Home-role request
│   ├── navigation/
│   │   └── LauncherHome.kt             # Main vertical launcher navigation/system deck
│   ├── data/
│   │   ├── LauncherApp.kt              # Application-level repository setup
│   │   ├── AppRepository.kt            # Installed-app discovery, cache, launch ranking
│   │   ├── AppDatabase.kt              # SQLite app/icon/launch-count cache
│   │   └── IconResolver.kt             # Lawnicons app-filter and SVG renderer
│   ├── sections/
│   │   ├── YourSpace.kt                # Clock, wallpaper, and dashboard pager
│   │   ├── YourApps.kt                 # Searchable/paginated app drawer
│   │   ├── PersonalDashboard.kt        # Weather, steps, contacts, UPI, music
│   │   ├── LauncherColors.kt           # Palettes, colors, and shared shapes
│   │   └── PriorityNotificationListener.kt
│   └── ui/theme/                       # Compose theme definitions
├── src/main/assets/lawnicons/          # Lawnicons mappings and SVG assets
├── src/main/res/                       # Android resources
└── THIRD_PARTY_NOTICES.md              # Third-party attribution
```

## App and Icon Handling

At startup, SwayLauncher loads cached app records, reconciles them with installed launcher activities, and watches for package changes. Apps are ordered by launch frequency and then alphabetically.

For icons, the launcher:

1. Looks for a matching Lawnicons SVG.
2. Uses a cached icon when available.
3. Falls back to the Android application icon.

Rendered icons and metadata are stored locally in `app_icons.db` for faster startup.

## Weather Data

Weather and city search use the public Open-Meteo APIs:

- Forecast: `api.open-meteo.com`
- City search: `geocoding-api.open-meteo.com`

Weather results are cached for 30 minutes. Saved device locations are reused for up to three hours; users can also select a city manually.

## Third-Party Assets

This project includes Lawnicons app-filter mappings and SVG icons.

- Project: [Lawnicons](https://github.com/LawnchairLauncher/lawnicons)
- License: Apache License 2.0
- Included license: `app/src/main/assets/lawnicons/LICENSE`
- Attribution: `app/THIRD_PARTY_NOTICES.md`

SwayLauncher is not affiliated with or endorsed by the Lawnchair or Lawnicons projects.

## Current Implementation Notes

- The notification-listener service for priority notifications is present, but its stored notifications are not yet rendered in the current launcher interface.
- The `QuickContacts` and `WallpaperPage` composables exist, but the active dashboard currently uses its own speed-dial contact section.
- Release builds currently use the debug signing configuration. Configure a proper release keystore before distributing an APK.
