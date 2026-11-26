# Anleitung

Kleine App für die Technik der Siedlung Rosenweg in Kaiseraugst, um NFC-Zutrittskarten (UniFi Access) zu verwalten. Karte hinten ans Telefon halten: die App zeigt, wem die Karte gehört, die Inventarnummer und den Status. Von dort lässt sich die Karte einer Person zuordnen, entziehen oder (wenn entzogen) aus dem Bestand löschen. Mit diesen Karten melden sich die Leute auch an den geteilten Rosenweg-Telefonen an.

## Im Rosenweg

- Nur für berechtigte Personen; die Telefonanlage prüft die Berechtigung bei jeder Aktion.
- **Geteilte Telefone:** Alles läuft über Rosenweg Gerät und die dort angemeldete Person; die App selbst hat dann keine Zugangsdaten.
- **Eigenes Handy:** Ohne Rosenweg Gerät meldet sich die App mit dem Rosenweg-Konto an (Modul `cloud` aus [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg)) und fragt die Telefonanlage direkt.
- Zuordnen über eine durchsuchbare Personenliste; Entziehen und Löschen nur mit Rückfrage.

## Installation

- **Geteilte Rosenweg-Telefone:** Rosenweg Gerät installiert die App aus dem App-Katalog der Telefonanlage, sofern sie für die angemeldete Person freigegeben ist.
- **Eigenes Handy:** APK von [www.rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps) herunterladen und installieren. Aktualisiert wird sie vom Rosenweg Updater der anderen Rosenweg-Apps.
