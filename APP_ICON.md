# App-Icon Anleitung

## Empfohlene Vorgehensweise

Das App-Icon sollte eine einfache Landschaft mit einem geschlängelten Weg zeigen.

### Option 1: Android Studio Image Asset Tool (Empfohlen)

1. Öffne Android Studio
2. Rechtsklick auf `res` Ordner → New → Image Asset
3. Wähle "Launcher Icons (Adaptive and Legacy)"
4. Wähle als Foreground Layer ein Icon oder importiere ein eigenes Bild
5. Setze Background Color auf #00BCD4 (Türkis)
6. Klicke auf "Next" und "Finish"

### Option 2: Eigenes Icon erstellen

Erstelle Bilder in folgenden Größen:
- mipmap-mdpi: 48x48 px
- mipmap-hdpi: 72x72 px
- mipmap-xhdpi: 96x96 px
- mipmap-xxhdpi: 144x144 px
- mipmap-xxxhdpi: 192x192 px

Design-Vorschlag:
- Hintergrund: Türkis (#00BCD4)
- Vordergrund: Weißer stilisierter Weg in S-Form
- Optional: Kleine Hügel oder Bäume

### Option 3: Online Icon Generator

Verwende einen Online-Generator wie:
- https://romannurik.github.io/AndroidAssetStudio/
- https://icon.kitchen/

Stelle sicher, dass:
- Die Primary Color #00BCD4 (Türkis) verwendet wird
- Das Icon einfach und klar erkennbar ist
- Es auf verschiedenen Hintergründen gut aussieht

## Platzierung

Die generierten Dateien sollten in folgenden Ordnern platziert werden:
```
app/src/main/res/
├── mipmap-mdpi/
│   ├── ic_launcher.png
│   └── ic_launcher_round.png
├── mipmap-hdpi/
│   ├── ic_launcher.png
│   └── ic_launcher_round.png
├── mipmap-xhdpi/
│   ├── ic_launcher.png
│   └── ic_launcher_round.png
├── mipmap-xxhdpi/
│   ├── ic_launcher.png
│   └── ic_launcher_round.png
└── mipmap-xxxhdpi/
    ├── ic_launcher.png
    └── ic_launcher_round.png
```

Das Android Studio Image Asset Tool erstellt diese automatisch.
