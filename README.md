# Rosenweg Karten

Kleine App für die Technik der Siedlung Rosenweg in Kaiseraugst, um NFC-Zutrittskarten (UniFi Access) zu verwalten. Karte hinten ans Telefon halten: die App zeigt, wem die Karte gehört, die Inventarnummer und den Status. Von dort lässt sich die Karte einer Person zuordnen, entziehen oder (wenn entzogen) aus dem Bestand löschen. Mit diesen Karten melden sich die Leute auch an den geteilten Rosenweg-Telefonen an.

## Im Rosenweg

- Nur für berechtigte Personen; die Telefonanlage prüft die Berechtigung bei jeder Aktion.
- **Geteilte Telefone:** Alles läuft über Rosenweg Gerät und die dort angemeldete Person; die App selbst hat dann keine Zugangsdaten.
- **Eigenes Handy:** Ohne Rosenweg Gerät meldet sich die App mit dem Rosenweg-Konto an (Modul `cloud` aus [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg)) und fragt die Telefonanlage direkt.
- Zuordnen über eine durchsuchbare Personenliste; Entziehen und Löschen nur mit Rückfrage.

## Installation

- **Geteilte Rosenweg-Telefone:** Rosenweg Gerät installiert die App aus dem App-Katalog der Telefonanlage, sofern sie für die angemeldete Person freigegeben ist.
- **Eigenes Handy:** APK von [www.rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps) herunterladen und installieren. Aktualisiert wird sie vom Rosenweg Updater der anderen Rosenweg-Apps.

## Bauen

Die App holt `ch.rosenweg4303:cloud` aus dem lokalen Maven-Repository (`mavenLocal()`), also zuerst [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg) veröffentlichen:

```bash
# in commons-rosenweg, Zweig rosenweg
./gradlew -PVERSION=7.1.3-rosenweg.1 publishToMavenLocal

# in diesem Repo
./gradlew assembleRelease
```

Signiert wird mit `keystore.properties` in diesem Ordner oder der von `android-sip-rosenweg` daneben (nie einchecken). Der Schlüssel muss derselbe sein wie bei Rosenweg Gerät. Paketname: `ch.rosenweg4303.karten`.

## Lizenz

Eigene Entwicklung für die Siedlung Rosenweg. Eine Lizenz ist noch nicht festgelegt (keine LICENSE-Datei im Repo).
