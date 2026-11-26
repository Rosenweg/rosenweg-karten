<p align="center"><img src="assets/logo/rosenweg-karten-512.png" width="96" alt="Logo"></p>
<h1 align="center">Rosenweg Karten</h1>
<p align="center">NFC-Zutrittskarten der Siedlung Rosenweg verwalten – für die Technik.</p>
<p align="center"><a href="https://www.rosenweg4303.ch/apps">Herunterladen</a> · <a href="docs/anleitung.md">Anleitung</a></p>

## Inhalt

- [Überblick](#überblick)
- [Plattformen](#plattformen)
- [Rechte und Gruppen](#rechte-und-gruppen)
- [Bedienung](#bedienung)
- [Geteilte Telefone und eigenes Handy](#geteilte-telefone-und-eigenes-handy)
- [Aufbau](#aufbau)
- [Bauen](#bauen)
- [CI und Release](#ci-und-release)
- [Konfiguration](#konfiguration)
- [Lizenz](#lizenz)

## Überblick

Kleine App für die Technik, um die NFC-Zutrittskarten (UniFi Access) zu verwalten. Mit denselben Karten melden sich die Leute auch an den geteilten Rosenweg-Telefonen an.

Was die App kann:

- **Karte lesen:** Karte hinten ans Telefon halten – Besitzer, Inventarnummer und Status.
- **Zuordnen:** Karte einer Person geben, über eine durchsuchbare Personenliste. Kennt Access die Karte noch nicht, wird sie als Fremdkarte angelegt.
- **Entziehen:** Zuordnung lösen; die Karte bleibt im Bestand.
- **Löschen:** entzogene Karten aus dem Access-Bestand entfernen.
- **Rückfragen:** Entziehen und Löschen nur mit Bestätigung.

Die Türleser übernehmen Änderungen nach etwa 30 Sekunden. Jede Änderung wird auf dem Server protokolliert (wer, welche Karte, an wen).

## Plattformen

| Plattform | Stand |
|---|---|
| Geteilte Rosenweg-Telefone | ja, über den App-Katalog der Telefonanlage (Rosenweg Gerät installiert sie) |
| Eigenes Android-Handy (ab Android 8) | ja, APK auf [rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps), Updates über den Rosenweg Updater |
| Andere Plattformen | nein (native Android-App) |

## Rechte und Gruppen

Die Berechtigung prüft die Telefonanlage bei **jeder** Aktion; die App selbst entscheidet nichts.

| Wer | darf |
|---|---|
| Gruppen `technik`, `präsident` (`praesident`) | Karten lesen, zuordnen, entziehen, löschen; Personenliste sehen |
| alle anderen | nichts (Antwort „nicht berechtigt“); im Katalog der geteilten Telefone erscheint die App nur für diese Gruppen |

Rosenweg Gerät prüft die Gruppen zusätzlich live, die Anlage nochmals an der Sitzung der Nebenstelle.

## Bedienung

| Schritt | So geht’s |
|---|---|
| Karte prüfen | App öffnen, Karte hinten ans Telefon halten |
| Zuordnen | Person in der Liste suchen und wählen. Gehört die Karte schon jemandem, kommt eine Meldung mit dem Namen – zuerst entziehen. |
| Entziehen | „Entziehen“, Rückfrage bestätigen |
| Löschen | nur bei entzogenen Karten, mit Rückfrage |

Hat eine Person noch keinen Benutzer in UniFi Access, kommt eine Fehlermeldung: zuerst dort anlegen. Fremdkarten müssen in Access erlaubt sein (Card Inventory › Third-Party NFC Cards).

Mehr: [docs/anleitung.md](docs/anleitung.md).

## Geteilte Telefone und eigenes Handy

| Gerät | Anmeldung | Weg der Anfragen |
|---|---|---|
| Geteiltes Rosenweg-Telefon | Karte am Sperrbildschirm (Rosenweg Gerät) | über Rosenweg Gerät an die Telefonanlage; die App hat keine Zugangsdaten |
| Eigenes Handy ohne Rosenweg Gerät | Rosenweg-Konto (Modul `cloud` aus commons-rosenweg) | direkt an die Telefonanlage |

Auf den geteilten Telefonen braucht die App die Rosenweg-Signatur (sonst „Rosenweg Gerät verweigert den Zugriff“) und Rosenweg Gerät ab Version 186.

## Aufbau

| Teil | Inhalt |
|---|---|
| `app/src/main/java/…/KartenActivity.java` | die ganze App: NFC lesen, Anzeige, Zuordnen, Entziehen, Löschen |
| `ch.rosenweg4303:cloud` | Anmeldung mit dem Rosenweg-Konto auf eigenen Handys ([commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg)) |
| `assets/logo/` | Logo aus dem Baukasten von rosenweg-basis: Wappen (Turm, Weg, Rose) mit Karten-Plakette, dazu ein monochromes Designsymbol (Android 13+) |

**Server:** Endpunkte `/api/telefon/karten/info`, `…/zuordnen`, `…/entziehen`, `…/loeschen`, `…/personen` der Telefonanlage; sie reicht an die Haupt-API und UniFi Access weiter (Rosenweg/Website, `pbx/GETEILTE-TELEFONE.md`).

Paketname `ch.rosenweg4303.karten`, minSdk 26, targetSdk 35.

## Bauen

Die App holt `ch.rosenweg4303:cloud` aus dem lokalen Maven-Repository, also zuerst commons-rosenweg veröffentlichen:

```bash
# in commons-rosenweg, Zweig main
./gradlew -PVERSION=7.1.3-rosenweg.1 publishToMavenLocal

# in diesem Repo
./gradlew assembleRelease
```

Signiert wird mit `keystore.properties` in diesem Ordner oder der von `android-sip-rosenweg` daneben (nie einchecken). Der Schlüssel muss derselbe sein wie bei Rosenweg Gerät.

Logo neu erzeugen (rosenweg-basis daneben): `sh assets/logo/erzeugen.sh`. Welches Zeichen die App trägt, steht in rosenweg-basis `werkzeug/logo/android-apps.txt`.

Mehr: [docs/entwickeln.md](docs/entwickeln.md).

## CI und Release

| Schritt | Was |
|---|---|
| APK bauen | lokal mit dem Rosenweg-Schlüssel (siehe oben) |
| Release | `gh release create v<versionName> <datei.apk> --notes "Was ist neu"` – Tag = versionName, eine APK je Release |
| Verteilen | `.github/workflows/apps.yml` (nutzt `apps-release.yml` aus Rosenweg/Website) schickt die APK an **[rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps)** und in den App-Katalog der Telefonanlage |
| Erneut senden | `gh workflow run apps.yml --ref <tag>` |

Anmeldung per GitHub-OIDC, kein gespeichertes Geheimnis. Die Anlage prüft Repo, Tag, Prüfsumme, Rosenweg-Signatur und Paketnamen; die App muss im Katalog (rosenweg4303.ch/smartphones) schon stehen.

## Konfiguration

Keine GitHub-Secrets. Lokal nur `keystore.properties` (Signierschlüssel, nie einchecken). Die Gruppen mit Zugriff stehen in der Telefonanlage (Rosenweg/Website, `pbx/`).

## Lizenz

GPL-3.0-or-later, siehe [LICENSE](LICENSE).
