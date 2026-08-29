# LED Controller – Android App für dein Arduino/WS2812-Projekt

Steuert deine LED-Strip via HC-05/HC-06 (klassisches Bluetooth SPP).
Farbwahl über ein Farbrad, Moduswahl, Helligkeit und Rainbow-Geschwindigkeit.

## 0. APK bauen ohne Android Studio (GitHub Actions)

Kein Android Studio nötig – GitHub baut die APK für dich in der Cloud.

1. Bei https://github.com kostenlos registrieren (falls noch kein Konto vorhanden).
2. Neues Repository anlegen: oben rechts **+ → New repository**, z. B. Name `led-controller`,
   Sichtbarkeit "Private" oder "Public" ist egal, **kein** README/​.gitignore/License anhaken.
3. Auf der leeren Repo-Seite auf **"uploading an existing file"** klicken.
4. Aus dem entpackten `LedController`-Ordner **alle Inhalte** (nicht den Ordner selbst,
   sondern `app/`, `.github/`, `gradle/`, `build.gradle`, `settings.gradle`, usw.)
   per Drag & Drop hochladen, dann unten **"Commit changes"** klicken.
   *(Wichtig: `build.gradle` und `settings.gradle` müssen direkt im Hauptverzeichnis des
   Repos liegen, nicht in einem Unterordner "LedController".)*
5. Oben im Repo auf den Reiter **"Actions"** klicken. Der Workflow "Build APK" startet
   automatisch (dauert ca. 3–6 Minuten). Grüner Haken = fertig.
6. Auf den fertigen Workflow-Lauf klicken, ganz unten bei **"Artifacts"** erscheint
   `LedController-debug-apk` zum Download – das ist eine ZIP-Datei mit der `app-debug.apk` drin.
7. ZIP entpacken, `app-debug.apk` aufs Handy übertragen (z. B. per Google Drive, E-Mail an
   dich selbst, oder USB-Kabel) und dort antippen. Android fragt evtl. nach Erlaubnis für
   "Installation aus unbekannten Quellen" – das ist normal bei manuell installierten Apps.

Falls der Workflow nicht automatisch startet: im Actions-Tab links "Build APK" auswählen,
dann rechts **"Run workflow"** klicken.

## 1. Projekt öffnen (alternativ: lokal mit Android Studio)

1. Android Studio installieren (falls noch nicht vorhanden): https://developer.android.com/studio
2. **File → Open…** und den Ordner `LedController` auswählen.
3. Android Studio fragt evtl. nach dem Gradle-Wrapper (`gradle-wrapper.jar` fehlt bewusst,
   da Binärdateien hier nicht mitgeliefert werden können). Einfach bestätigen, wenn
   Android Studio anbietet, den Wrapper automatisch herunterzuladen/zu reparieren
   ("Gradle Wrapper missing → OK" bzw. **File → Sync Project with Gradle Files**).
4. Warten bis der Gradle-Sync durchgelaufen ist (Internetverbindung nötig, lädt Abhängigkeiten).

## 2. App installieren

- Handy per USB anschließen, USB-Debugging aktivieren (Entwickleroptionen).
- Auf **Run ▶** klicken, Gerät auswählen.
- Alternativ: **Build → Build Bundle(s)/APK(s) → Build APK(s)**, die fertige APK liegt danach unter
  `app/build/outputs/apk/debug/app-debug.apk` und kann manuell per USB/Cloud aufs Handy kopiert werden.

## 3. Vor der ersten Nutzung

- Dein HC-05/HC-06-Modul muss **einmalig in den normalen Android-Bluetooth-Einstellungen
  gekoppelt** werden (PIN meist `1234` oder `0000`). Die App zeigt nur bereits gekoppelte Geräte an.
- **Wichtig:** Behebe den Bug in deinem Arduino-Sketch (siehe Chat) – die Zeile mit `scanf`
  kompiliert so nicht:
  ```cpp
  int r, g, b;
  int values = sscanf(data + 1, "%d,%d,%d", &r, &g, &b);
  ```

## 4. Bedienung der App

- **Oben:** Gerät aus der Liste wählen → "Verbinden" tippen. Status wird angezeigt.
- **Farbrad:** Farbton/Sättigung frei wählen. Sendet automatisch `N{r},{g},{b}~` an den
  Arduino und schaltet ihn in den Festfarben-Modus (mode = 0).
- **Modus-Buttons:**
  - *Farbe*: aktiviert wieder die zuletzt gewählte Festfarbe
  - *Rainbow*: sendet `M1~`
  - *Rainbow Slow*: sendet `M2~`
- **Helligkeit-Regler:** 0–255, sendet `B{n}~` (entspricht `FastLED.setBrightness`).
- **Rainbow-Geschwindigkeit-Regler:** 10–150 (kleiner Wert = schneller), sendet `R{n}~`.

## 5. Protokoll-Übersicht (muss exakt zum Arduino-Sketch passen)

| Befehl | Bedeutung | Beispiel |
|---|---|---|
| `N{r},{g},{b}~` | Feste Farbe setzen (0–255 je Kanal) | `N255,128,0~` |
| `M{n}~` | Modus setzen (0=Farbe, 1=Rainbow, 2=Rainbow Slow) | `M1~` |
| `B{n}~` | Helligkeit (0–255) | `B180~` |
| `R{n}~` | Rainbow-Geschwindigkeit (10–150) | `R25~` |

## 6. Technische Hinweise

- Minimale Android-Version: 7.0 (API 24)
- Auf Android 12+ wird die neue `BLUETOOTH_CONNECT`-Berechtigung zur Laufzeit angefragt.
- Die App throttelt Sende-Befehle beim Ziehen der Regler/Farbrads (max. alle 120 ms),
  damit der 32-Byte-Puffer des Arduino nicht überläuft. Beim Loslassen wird immer noch
  einmal final gesendet.
- Verbindungsaufbau und Schreiben laufen auf einem Hintergrundthread, die UI blockiert nie.
