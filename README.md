<p align="center"><img src="assets/logo/rosenweg-karten-512.png" width="96" alt="Logo"></p>
<h1 align="center">Rosenweg Karten</h1>
<p align="center">Rosenweg-Karten nachschlagen, Personen zuordnen, entziehen und aus dem Bestand löschen.</p>
<p align="center"><a href="docs/anleitung.md">Anleitung</a> · <a href="https://www.rosenweg4303.ch/apps">Herunterladen</a></p>

Kartenverwaltung für Technik und Präsident: Rosenweg-Karte (UniFi Access) hinhalten oder auflegen, sehen, wem sie gehört, sie einer Person zuordnen, entziehen oder aus dem Bestand löschen. Dazu das Kartenprotokoll und die Karten-, QR- und Handy-Anmeldungen der Apps aller Personen.

Ab 2.0.0 eine Flutter-App für Linux, macOS, Windows, Android und iOS; sie ersetzt die frühere Android-App (Java, bis 1.6.3) mit **derselben Kennung** `ch.rosenweg4303.karten`, demselben Rosenweg-Schlüssel und höherem `versionCode` (11 statt 10) – Android spielt sie als gewöhnliches Update darüber.

Angelegt mit `werkzeug/neue-app.sh` aus der App-Vorlage von
[rosenweg-basis](https://github.com/Rosenweg/rosenweg-basis). Anmeldung, API,
Gerüst, Updater, Kartenleser und Logo kommen aus der Basis.

## Was die App kann

| Bereich | Inhalt | Woher |
|---|---|---|
| Karte | Karte lesen (Handy: NFC; Station/Computer: USB-Leser über PC/SC, z. B. ACR122U; iPhone: auf Knopfdruck) oder Nummer eintippen → Inhaber, Inventarnummer, Zustand, UID; **Einer Person zuordnen** (Suche nach Name/E-Mail), **Entziehen**, **Aus dem Bestand löschen** (nur entzogene) | Telefonanlage `POST /api/telefon/karten/info\|zuordnen\|entziehen\|loeschen`, `GET …/personen`; auf geteilten Telefonen über Rosenweg Gerät (`karten_*`) |
| Protokoll | Kartenprotokoll 30 Tage: Zuordnen, Entziehen, Löschen, Anmeldungen, **Admin mit Karte**, Bestätigungen; Suche und Filter | Haupt-API `GET /api/karten-protokoll` |
| Anmeldungen | Karten-, QR- und Handy-Anmeldungen der Apps aller Personen; eine beenden oder **alle Apps einer Person** (Karte verloren) | `GET /api/apps/sitzungen?alle=1`, `DELETE /api/apps/sitzungen/:id`, `POST …/alle-abmelden` – nur mit dem Rosenweg-Konto (die API sagt das mit der Karte) |

**Karte wie Schlüssel:** Mit der Karte angemeldet hat man dieselben Rechte wie mit dem Konto. Die App sagt das oben („wird protokolliert, Quittung per E-Mail“), zeigt offene Bestätigungen (`GET /api/me/bestaetigungen`) und – wenn die API eine Aktion erst nach Bestätigung per E-Mail ausführt (202) – „Bestätigung per E-Mail nötig“ statt „erledigt“.

Wer nicht in Technik oder Präsident ist, sieht nur „Die Kartenverwaltung ist nur für Technik und Präsident.“ – die Anlage prüft es ohnehin.

## Läuft auf

| Plattform | Datei | Grenzen |
|---|---|---|
| Linux (Debian 13, Stationen und eigene Rechner) | `rosenweg-karten_<version>_amd64.deb` | Stationen bekommen das Paket vom PXE-Server (os-stationen), dort ist der Updater aus |
| Android 8 und neuer | `rosenweg-karten-<version>.apk` (Rosenweg-Schlüssel) | auf geteilten Telefonen meldet Rosenweg Gerät an und ab |
| Windows 10/11 | `Rosenweg-Karten-<version>-Setup.exe` | unsigniert, solange kein Code-Signing-Zertifikat da ist (SmartScreen fragt einmal) |
| macOS 11 und neuer | `Rosenweg-Karten-<version>.dmg` | ohne Apple Developer ID ad hoc signiert (Gatekeeper fragt beim ersten Start) |
| iOS/iPadOS | – | baut in CI ohne Signatur, verteilt wird erst mit dem Apple Developer Program |

Herunterladen: [www.rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps).

## Bedienung

Siehe [docs/anleitung.md](docs/anleitung.md).

## Entwickeln

```sh
# rosenweg-basis daneben auschecken, dann lokal gegen sie bauen:
printf 'dependency_overrides:\n  rosenweg_basis:\n    path: ../rosenweg-basis\n' > pubspec_overrides.yaml
flutter pub get
flutter run -d macos                       # oder linux, windows, ein Handy
flutter run -d macos --dart-define=RW_ATTRAPPE=1   # ohne Server, mit Vorführdaten (lib/attrappe.dart)
flutter analyze && flutter test
RW_FOTO=$HOME/Downloads flutter test test/bildschirmfoto_test.dart   # Bildschirmfotos
```

Vor dem Commit `pubspec_overrides.yaml` entfernen und `flutter pub get` laufen lassen –
`pubspec.lock` darf nicht auf `../rosenweg-basis` zeigen (die CI bricht sonst ab).

| Ordner | Inhalt |
|---|---|
| `lib/app.dart` | `RosenwegApp`: Kennung `ch.rosenweg4303.karten`, Name, Logo |
| `lib/seiten/` | die Seiten der App (`RosenwegGeruest`: Handy unten, Desktop links) |
| `lib/attrappe.dart` | Vorführdaten statt der API (Tests, Bildschirmfotos) |
| `assets/logo/` | Logo aus dem Baukasten der Basis: `sh assets/logo/erzeugen.sh ../rosenweg-basis` |
| `linux/paket/` | Debian-Paket (`bauen.sh`, im Bau-Container `im-container.sh`), `paket.conf` = Vorlage für os-stationen |
| `macos/installer/` | DMG (`bauen.sh`) |
| `windows/installer/` | Inno-Setup-Installer |
| `.github/workflows/` | `app.yml` (Bau, Release) und `apps.yml` (an /apps) – die Arbeit macht rosenweg-basis |

## Veröffentlichen

1. Version in `pubspec.yaml` erhöhen (`version: x.y.z+n`, Baunummer immer mit).
2. Pull Request, CI grün (Linux und Android), Merge auf main.
3. Die CI baut alle fünf Plattformen, legt das Release `vx.y.z` an und schickt
   APK, `.deb`, DMG und Installer an `/apps`. Ohne neue Version passiert nach dem
   Merge nur die CI.
4. Stationen: `linux/paket/paket.conf` in os-stationen (`pakete/rosenweg-karten/`)
   mit Version und Prüfsumme nachführen.

## Lizenz

GPL-3.0-or-later, siehe [LICENSE](LICENSE). Logo-Zeichen: siehe rosenweg-basis
`werkzeug/logo/zeichen/LIZENZEN.md`.
