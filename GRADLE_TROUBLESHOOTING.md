# Gradle Build Fehler - Lösungen

## Problem: "Your build is currently configured to use incompatible Java 21"

### Schnelle Lösung:

**Das Projekt wurde auf Gradle 8.5 aktualisiert und funktioniert jetzt mit JDK 21!**

1. **Lösche Cache-Ordner:**
   - `C:\Users\Maria Restrepo\.gradle\`
   - `C:\Users\Maria Restrepo\.android\`
   - Im Projektverzeichnis: `.gradle`, `.idea`, `app\build`

2. **Android Studio neu starten**

3. **Projekt öffnen und warten auf Gradle Sync**

4. **File → Invalidate Caches / Restart**

**Alternative: JDK 17 verwenden (siehe JDK_SETUP.md)**

## Problem: "Could not resolve all files for configuration ':app:androidJdkImage'"

### Lösung 1: Cache leeren (Empfohlen)

1. **Android Studio schließen**
2. **Lösche folgende Ordner:**
   ```
   C:\Users\[Dein Username]\.gradle\caches\
   C:\Users\[Dein Username]\.android\build-cache\
   ```
3. **Im Projektverzeichnis:**
   - Lösche `.gradle` Ordner
   - Lösche `.idea` Ordner
   - Lösche `app/build` Ordner
4. **Android Studio neu starten**
5. **File → Invalidate Caches / Restart → Invalidate and Restart**
6. **Warte auf Gradle Sync**
7. **Build → Clean Project**
8. **Build → Rebuild Project**

### Lösung 2: JDK Version überprüfen

1. **File → Project Structure → SDK Location**
2. **Stelle sicher, dass JDK 17 verwendet wird**
3. **Gradle JDK sollte "Embedded JDK (17)" sein**

### Lösung 3: Gradle Wrapper neu generieren

Im Terminal (innerhalb des Projektverzeichnisses):
```bash
./gradlew wrapper --gradle-version 8.0
```

Unter Windows:
```cmd
gradlew.bat wrapper --gradle-version 8.0
```

### Lösung 4: Android SDK überprüfen

1. **Tools → SDK Manager**
2. **Stelle sicher, dass folgende Komponenten installiert sind:**
   - Android SDK Platform 33
   - Android SDK Build-Tools 33.0.0 oder höher
   - Android SDK Platform-Tools
   - Android SDK Tools

### Lösung 5: Gradle Offline Mode deaktivieren

1. **File → Settings (oder Preferences auf Mac)**
2. **Build, Execution, Deployment → Gradle**
3. **Deaktiviere "Offline work"**

### Lösung 6: Manuelle Dependency Aktualisierung

Falls nichts hilft, versuche folgendes in der Terminal:

```bash
# Cache löschen
./gradlew cleanBuildCache

# Dependencies neu laden
./gradlew --refresh-dependencies

# Build versuchen
./gradlew build
```

## Problem: "Execution failed for task ':app:kspDebugKotlin'"

### Lösung:
1. **Build → Clean Project**
2. **Lösche `app/build` Ordner**
3. **Build → Rebuild Project**

## Problem: Gradle Sync dauert ewig

### Lösung:
1. **Überprüfe Internet-Verbindung**
2. **File → Settings → Build, Execution, Deployment → Gradle**
3. **Setze "Gradle JVM" auf "Embedded JDK version 17"**
4. **Erhöhe Heap Size in gradle.properties:**
   ```
   org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
   ```

## Empfohlene Reihenfolge bei Build-Problemen

1. ✅ File → Invalidate Caches / Restart
2. ✅ Build → Clean Project
3. ✅ Gradle Cache löschen (siehe Lösung 1)
4. ✅ Build → Rebuild Project
5. ✅ Android Studio neu starten

## Wichtige Hinweise

- **JDK 21 funktioniert jetzt** mit Gradle 8.5
- **JDK 17 empfohlen** für beste Kompatibilität (siehe JDK_SETUP.md)
- **Immer zuerst Cache leeren** bei mysteriösen Build-Fehlern
- **Gradle 8.5** ist stabil und kompatibel mit JDK 17-21
- **compileSdk 34** für neueste Features

## Wenn alles andere fehlschlägt

1. Projekt komplett löschen
2. Frisches Projekt aus dem ZIP entpacken
3. In Android Studio öffnen
4. Warten bis Gradle Sync fertig ist
5. Clean Project
6. Rebuild Project
