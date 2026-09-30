# Freizeit

What to do with your free time: your favorite places around Aachen, suggested by distance, weather and opening hours.

- **Suggestions** from your favorite and want-to-go places, ranked by distance, weather and whether they're open
- **Map** of playgrounds, parks, cafés, ice cream and more from OpenStreetMap, with category filters and search
- **Mark** places as favorite or want-to-go, and add your own places the map doesn't know
- **Check in** when you visit — manually or from a nearby-place notification — and see your history
- **Widget** on the home screen with today's top 3 suggestions
- **Back up** and restore your places and check-ins

## Build

Android 8+ (API 26), Android Studio, JDK 17–21 (Android Studio's bundled JBR works; newer JDKs break Gradle 8.5).
Clone, open, run.

The app starts empty. Generate a `pois.json` with [`tools/poi_extraction`](tools/poi_extraction) from any
OpenStreetMap `.osm.pbf` extract (for example from [Geofabrik](https://download.geofabrik.de/)), then import it in Settings.

Built with Kotlin, Jetpack Compose, Room, MapLibre, Glance and WorkManager; weather from Open-Meteo.
Developed with [Claude Code](https://claude.ai/code).
