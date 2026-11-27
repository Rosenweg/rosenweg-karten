// SPDX-License-Identifier: GPL-3.0-or-later
import 'dart:convert';

import 'package:rosenweg_basis/rosenweg_basis.dart';

import 'daten.dart';

/// Telefonanlage: dort liegen die Karten-Routen (pbx/api/app.py,
/// /api/telefon/karten/*), dieselben wie fuer die alte Android-App.
const anlageBasis = 'https://pbx.rosenweg4303.ch';

/// Karten nachschlagen, zuordnen, entziehen, loeschen; Personen fuer die
/// Auswahl. Die Anlage prueft Technik/Praesident und protokolliert jede
/// Aktion (wer, welche Karte, fuer wen).
class KartenApi {
  KartenApi(this._transport);

  /// Eigenes Handy, Computer, Station: direkt an die Anlage mit dem Zugang der
  /// Anmeldung (Karte, QR, Konto). Geteiltes Telefon: ueber Rosenweg Geraet
  /// (wie die alte App), die Anlage prueft die dort angemeldete Person.
  factory KartenApi.fuer(RosenwegSitzung s) => KartenApi(
        s.geteiltesTelefon ? GeraetKartenTransport() : HttpTransport(basis: anlageBasis, zugang: s.zugang),
      );

  final Transport _transport;

  Future<Map<String, dynamic>> _post(String pfad, Map<String, Object?> koerper) async {
    final j = apiAntwortPruefen(await _transport.senden('POST', pfad, koerper: koerper));
    return j is Map<String, dynamic> ? j : const {};
  }

  Future<Karte> nachschlagen(String uid) async => Karte.aus(uid, await _post('/api/telefon/karten/info', {'uid': uid}));

  Future<List<Person>> personen() async {
    final j = apiAntwortPruefen(await _transport.senden('GET', '/api/telefon/karten/personen'));
    final l = j is Map ? j['personen'] : null;
    return [
      for (final p in l is List ? l : const []) if (p is Map<String, dynamic>) Person.aus(p),
    ]..removeWhere((p) => p.email.isEmpty);
  }

  /// {ok, schon?, importiert?, display_id, name}
  Future<Map<String, dynamic>> zuordnen(String uid, String email) =>
      _post('/api/telefon/karten/zuordnen', {'uid': uid, 'email': email});

  Future<Map<String, dynamic>> entziehen(String uid) => _post('/api/telefon/karten/entziehen', {'uid': uid});

  Future<Map<String, dynamic>> loeschen(String uid) => _post('/api/telefon/karten/loeschen', {'uid': uid});
}

/// Geteiltes Telefon: die Karten-Methoden des AnlageProviders von Rosenweg
/// Geraet (karten_info, karten_personen, karten_zuordnen, karten_entziehen,
/// karten_loeschen; ab v186), wie in der alten Java-App.
class GeraetKartenTransport implements Transport {
  GeraetKartenTransport({GeraetRufenRoh? rufen}) : _rufen = rufen ?? RosenwegPlattform.geraetRufen;
  final GeraetRufenRoh _rufen;

  @override
  Future<ApiAntwort> senden(String methode, String pfad, {Object? koerper}) async {
    final k = koerper is Map ? koerper : const {};
    final (name, arg, extras) = switch (pfad) {
      '/api/telefon/karten/personen' => ('karten_personen', 'personen', null),
      '/api/telefon/karten/info' => ('karten_info', '${k['uid']}', null),
      '/api/telefon/karten/zuordnen' => ('karten_zuordnen', '${k['uid']}', <String, Object?>{'email': '${k['email']}'}),
      '/api/telefon/karten/entziehen' => ('karten_entziehen', '${k['uid']}', null),
      '/api/telefon/karten/loeschen' => ('karten_loeschen', '${k['uid']}', null),
      _ => throw GeraetFehler('Unbekannte Anfrage an Rosenweg Gerät.'),
    };
    final b = await _rufen(name, arg: arg, extras: extras);
    return geraetKartenAntwort(b);
  }
}

typedef GeraetRufenRoh = Future<Map<String, Object?>> Function(String methode, {String? arg, Map<String, Object?>? extras});

/// Bundle des AnlageProviders → Antwort (oeffentlich fuer Tests).
ApiAntwort geraetKartenAntwort(Map<String, Object?> b) {
  switch (b['fehler_art']) {
    case 'signatur':
      throw GeraetFehler('Rosenweg Gerät verweigert den Zugriff (andere Signatur?).');
    case 'nicht_installiert':
      throw GeraetFehler('Rosenweg Gerät ist nicht installiert.');
    case 'unbekannt':
      throw GeraetFehler('Rosenweg Gerät: ${b['fehler'] ?? 'unbekannter Fehler'}');
  }
  if (b['nicht_berechtigt'] == true) {
    return (status: 403, json: {'error': 'Die Kartenverwaltung ist nur für Technik und Präsident.'});
  }
  final j = b['json'];
  if (j is String) {
    try {
      return (status: 200, json: jsonDecode(j));
    } on FormatException {
      throw ServerNichtErreichbar();
    }
  }
  if (b['fehler'] is String) return (status: 502, json: {'error': b['fehler']});
  throw GeraetFehler('Rosenweg Gerät ist nicht eingerichtet oder zu alt (mindestens Version 186).');
}
