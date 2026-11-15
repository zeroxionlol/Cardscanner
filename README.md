# Cardscanner

Android-App zum Scannen von Pokémon-Karten: Karte fotografieren, die App erkennt sie,
zeigt den aktuellen Cardmarket-Preis und speichert Foto und Karte in deiner Sammlung.

## Funktionen

- **Scannen** – Foto mit der Kamera (oder aus der Galerie). Die Texterkennung läuft direkt
  auf dem Handy und liest Kartenname und Kartennummer (z. B. `125/197`).
- **Zuordnung** – Aus Nummer, Setgröße und Name wird die Karte gesucht. Bei mehreren
  Treffern wählst du aus; findet die App nichts, kannst du nach dem Namen suchen.
- **Cardmarket-Preis** – Preistrend, günstigstes Angebot sowie Durchschnitt über 1, 7 und 30 Tage,
  getrennt für Normal und Holo/Reverse.
- **Sammlung** – Deine Fotos, Anzahl pro Karte, Gesamtwert, Suche und Sortierung.
- **Preise aktualisieren** – Ein Button für die ganze Sammlung, einer pro Karte.
- **Kategorien** – Eigene Kategorien (z. B. „Lenas Karten“). Oben in der Kamera wählst du per
  Dropdown, in welche Kategorie die nächsten Scans kommen.
- **Farbwelten** – Klassik, Feuer, Wasser, Pflanze, Elektro oder Psycho: über das Paletten-Symbol
  auf der Wert-Kachel umschalten, die ganze App färbt sich um.
- **Binder** – Binder mit eigenem Coverfoto aus der Galerie und 2 × 2, 3 × 3 oder 4 × 3 Taschen
  pro Seite. Eine leere Tasche antippen und eine Karte aus der Sammlung einstecken; lange drücken
  zum Verschieben, Seiten hinzufügen und entfernen.

## Woher die Preise kommen

Cardmarket vergibt derzeit keine neuen API-Zugänge. Die App nutzt deshalb die freie
[TCGdex-API](https://tcgdex.dev), die den täglichen Cardmarket-Preisführer einbindet.
Die Preise sind also Tageswerte von Cardmarket (Euro), keine Live-Angebote.

## Installieren

Jeder Push auf `main` baut die App automatisch (GitHub Actions) und legt sie unter
**Releases → latest** als `Cardscanner.apk` ab. Auf dem Handy herunterladen, öffnen und
die Installation aus dieser Quelle erlauben.

Alle Builds sind mit demselben Schlüssel signiert (`app/debug.keystore`), damit sich
Updates über die alte Version installieren lassen und die Sammlung erhalten bleibt.

## Selbst bauen

```
./gradlew assembleDebug
```

Die APK liegt danach in `app/build/outputs/apk/debug/`.

## Aufbau

| Ordner | Inhalt |
| --- | --- |
| `app/src/main/java/.../data` | Kartenmodelle, TCGdex-Client, Speicherung der Sammlung |
| `app/src/main/java/.../scan` | Fotoimport, Texterkennung, Auswertung des Kartentexts, Kartensuche |
| `app/src/main/java/.../ui` | Oberfläche (Jetpack Compose, Material 3) |

Die Sammlung liegt nur auf dem Gerät (App-Speicher), es gibt kein Konto und keinen eigenen Server.

Dies ist ein privates Fan-Projekt und steht in keiner Verbindung zu Nintendo, The Pokémon Company oder Cardmarket.
