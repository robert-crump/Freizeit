# Installation & Setup - Freizeit App

## Voraussetzungen

- Android Studio (Arctic Fox oder neuer)
- Android SDK (API Level 24+)
- Ein Android-Gerät oder Emulator mit Android 7.0+

## Installation

### 1. Projekt in Android Studio öffnen

1. Starte Android Studio
2. File → Open
3. Navigiere zum `freizeit-app` Ordner
4. Klicke auf "OK"

### 2. Gradle Sync

Android Studio sollte automatisch Gradle synchronisieren. Falls nicht:
- File → Sync Project with Gradle Files

**Bei Gradle-Fehlern:**
- File → Invalidate Caches / Restart → Invalidate and Restart
- Lösche den `.gradle` Ordner im Projektverzeichnis
- Lösche den `.idea` Ordner im Projektverzeichnis
- Öffne das Projekt erneut in Android Studio

### 3. SDK Installation

Falls Android Studio fehlende SDK-Komponenten meldet:
- Tools → SDK Manager
- Installiere alle empfohlenen Komponenten
- Stelle sicher, dass API Level 24 (Android 7.0) oder höher installiert ist

### 4. Build & Run

**Option A: Auf Emulator**
1. Tools → Device Manager
2. Create Device → Wähle ein Gerät (z.B. Pixel 5)
3. Wähle System Image (API 33 oder höher empfohlen)
4. Klicke auf "Run" (grüner Play-Button)

**Option B: Auf physischem Gerät**
1. Aktiviere "Entwickleroptionen" auf deinem Android-Gerät:
   - Einstellungen → Über das Telefon → 7x auf Build-Nummer tippen
2. Aktiviere "USB-Debugging" in den Entwickleroptionen
3. Verbinde dein Gerät via USB
4. Klicke auf "Run" und wähle dein Gerät

## Erste Schritte nach Installation

### 1. Standortberechtigung erteilen

Beim ersten Start fragt die App nach Standortberechtigung. Diese ist wichtig für:
- Automatische Standorterkennung
- Distanzberechnung zu Aktivitäten
- Erkennung von Favorit-Adressen

### 2. Favorit-Adressen anlegen

1. Öffne Einstellungen (⋮ Menü → Einstellungen)
2. Klicke auf das Plus-Symbol (+)
3. Gib die Adressdaten ein (z.B. deine Heimatadresse in Hamburg oder Aachen)
4. Klicke auf "Standort bestimmen"
5. Überprüfe auf der Karte, ob der Standort korrekt ist
6. Klicke auf "Speichern"

### 3. Erste Aktivität erstellen

1. Gehe zurück zum Hauptbildschirm
2. Wähle eine Kategorie (z.B. "Café")
3. Klicke auf das Plus-Symbol (+)
4. Gib die Details ein:
   - Name (z.B. "Café Central")
   - Kategorie
   - Indoor/Outdoor
   - Adresse
5. Klicke auf "Standort bestimmen"
6. Klicke auf "Speichern"

### 4. Aktivitäten entdecken

- **Nach Kategorie**: Klicke auf eine Kategorie-Kachel
- **Zufällig**: Nutze das Würfel-Symbol (🎲) für eine zufällige Aktivität
- **Karte**: In der Aktivitätsliste auf das Karten-Symbol tippen
- **Filter**: Nutze die Filter-Chips (Entfernung, Indoor/Outdoor)

## Daten sichern

### Export
1. Einstellungen öffnen
2. "Datenbank exportieren" klicken
3. Speicherort wählen
4. JSON-Datei wird erstellt

### Import
1. Einstellungen öffnen
2. "Datenbank importieren" klicken
3. JSON-Datei auswählen
4. Bestätigen

## Bekannte Einschränkungen

- **Internet erforderlich für**:
  - Geocoding (Adresse → Koordinaten)
  - Kartendarstellung (OSMDroid lädt Tiles)
  - Keine Internet-Verbindung? Die App funktioniert trotzdem, aber ohne Karten

- **Standortgenauigkeit**:
  - GPS muss aktiviert sein für beste Ergebnisse
  - Favorit-Adressen werden bei <300m Entfernung erkannt

- **Geocoding-Limitierung**:
  - Nominatim API (kostenlos) hat Rate Limits
  - Bei vielen Geocoding-Anfragen kurz warten

## Troubleshooting

### Gradle Build Fehler
- **"Plugin not found" Fehler**:
  1. File → Invalidate Caches / Restart
  2. Lösche `.gradle` und `.idea` Ordner
  3. Öffne Projekt neu
- **"Could not resolve plugin artifact"**:
  1. Überprüfe Internet-Verbindung
  2. File → Sync Project with Gradle Files
  3. Build → Clean Project, dann Rebuild Project

### App startet nicht
- Überprüfe, ob alle Dependencies korrekt heruntergeladen wurden
- Build → Clean Project
- Build → Rebuild Project

### Karte wird nicht angezeigt
- Internetverbindung überprüfen
- OSMDroid benötigt Internet für Kartentiles
- Manchmal hilft ein Neustart der App

### Standort wird nicht ermittelt
- GPS aktivieren
- Standortberechtigung in Android-Einstellungen überprüfen
- Im Freien testen (GPS funktioniert in Gebäuden oft schlecht)

### Geocoding schlägt fehl
- Internetverbindung überprüfen
- Adresse vollständig und korrekt eingeben
- Alternative Schreibweise versuchen

## Support

Bei Fragen oder Problemen:
- Überprüfe die Logs in Android Studio (Logcat)
- Stelle sicher, dass alle Dependencies korrekt sind
- Überprüfe die build.gradle Dateien

## Viel Spaß mit der Freizeit App! 🎉
