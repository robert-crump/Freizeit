# Freizeit

An Android app for discovering and managing leisure activities and day trips in Hamburg and Aachen.

## Features

- **Activity Browser** — Browse activities organized by category (hiking, cycling, culture, ice cream, and more)
- **Map View** — View activities on an interactive OpenStreetMap map
- **Distance & Travel Time** — Automatic distance calculation (Haversine) and estimated travel time from your current location
- **Filters** — Filter activities by category, distance, and indoor/outdoor
- **Random Activity** — Swipe through random activity suggestions (ViewPager2)
- **Favorite Locations** — Save home addresses or frequent locations; auto-detected when within 300 m
- **Geocoding** — Convert addresses to coordinates using the Nominatim API
- **Export / Import** — Back up and restore your activity data as JSON

## Screenshots

_Coming soon_

## Requirements

- Android 7.0 (API 24) or higher
- Android Studio Hedgehog or later
- JDK 21

## Getting Started

1. Clone the repository
   ```bash
   git clone https://github.com/robert-crump/Freizeit.git
   ```
2. Open the project in Android Studio
3. Let Gradle sync and download dependencies
4. Run on an emulator or physical device (API 24+)

## Architecture

```
com.example.freizeit/
├── data/
│   ├── entity/          # Room entities (Activity, FavoriteLocation)
│   ├── dao/             # Data Access Objects
│   ├── repository/      # Repository layer
│   ├── AppDatabase.kt
│   └── Converters.kt
├── ui/
│   ├── activity/        # ActivityListActivity, ActivityEditActivity, RandomActivityActivity
│   ├── settings/        # SettingsActivity, FavoriteLocationEditActivity
│   ├── adapter/         # RecyclerView adapters
│   ├── viewmodel/       # MainViewModel
│   └── MainActivity.kt
└── util/
    ├── GeocodingService.kt
    ├── LocationManager.kt
    ├── FilterOptions.kt
    └── DatabaseExportImport.kt
```

## Tech Stack

| Library | Version | Purpose |
|---|---|---|
| Room | 2.6.1 | Local database |
| OSMDroid | 6.1.18 | OpenStreetMap map tiles |
| OSMBonusPack | 6.9.0 | Map overlays |
| Google Location Services | 21.1.0 | GPS / fused location |
| OkHttp | 4.12.0 | HTTP client (Nominatim geocoding) |
| Gson | 2.10.1 | JSON export/import |
| Material Design 3 | 1.11.0 | UI components |
| Kotlin Coroutines | 1.7.3 | Async operations |

## Development

This project was developed with assistance from [Claude Code](https://claude.ai/code) by Anthropic.

© 2026 All rights reserved.
