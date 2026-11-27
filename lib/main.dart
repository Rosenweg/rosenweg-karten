// SPDX-License-Identifier: GPL-3.0-or-later
import 'package:rosenweg_basis/rosenweg_basis.dart';

import 'app.dart';
import 'attrappe.dart';
import 'karten_api.dart';
import 'seiten/haupt.dart';

/// rosenwegStarten (rosenweg_basis): Logdatei, Fehlerfang, Anmeldung
/// (Anmeldeschirm mit Karte, QR, Konto; an Stationen still per Kerberos;
/// auf geteilten Telefonen ueber Rosenweg Geraet) und der Updater
/// (aus auf Rosenweg-Stationen und mit Rosenweg Geraet). Erst danach kommt
/// [Haupt].
void main() {
  if (attrappe) {
    final a = Attrappe();
    rosenwegStarten(appAngaben, (s) => Haupt(sitzung: s, kartenApi: KartenApi(a)), sitzung: RosenwegSitzung(appAngaben, transport: a));
  } else {
    rosenwegStarten(appAngaben, (s) => Haupt(sitzung: s));
  }
}
