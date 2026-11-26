# Für Entwickler

Android-App (Gradle), Paketname `ch.rosenweg4303.karten`.

## Bauen

Die App holt `ch.rosenweg4303:cloud` aus dem lokalen Maven-Repository (`mavenLocal()`), also zuerst [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg) veröffentlichen:

```bash
# in commons-rosenweg, Zweig rosenweg
./gradlew -PVERSION=7.1.3-rosenweg.1 publishToMavenLocal

# in diesem Repo
./gradlew assembleRelease
```

Signiert wird mit `keystore.properties` in diesem Ordner oder der von `android-sip-rosenweg` daneben (nie einchecken). Der Schlüssel muss derselbe sein wie bei Rosenweg Gerät. Paketname: `ch.rosenweg4303.karten`.

Logo neu erzeugen (aus dem Repo-Wurzelverzeichnis, rosenweg-basis daneben): `sh assets/logo/erzeugen.sh`. Welches Zeichen die App trägt, steht in rosenweg-basis `werkzeug/logo/android-apps.txt`.

## Neue Version

APK wie oben bauen und signieren, dann `gh release create v<versionName> <datei.apk> --notes "Was ist neu"` (Tag = versionName der APK, eine APK je Release). Der Workflow `.github/workflows/apps.yml` (wiederverwendbarer Workflow `apps-release.yml` aus Rosenweg/Website) schickt sie an [www.rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps) und in den App-Katalog der Telefonanlage; damit bekommen sie die geteilten Telefone und der Rosenweg Updater. Anmeldung per GitHub-OIDC, kein gespeichertes Geheimnis; die Anlage prüft Tag, Prüfsumme, Rosenweg-Signatur und Paketnamen. Erneut senden: `gh workflow run apps.yml --ref <tag>`.

## Logo

Rosenweg-Wappen (Turm, Weg, Rose auf Rot) mit dem Zeichen der App rechts unten, erzeugt mit dem Logo-Baukasten aus rosenweg-basis; dazu ein monochromes Designsymbol (Android 13+).
