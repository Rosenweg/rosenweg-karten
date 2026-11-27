// SPDX-License-Identifier: GPL-3.0-or-later
import 'package:rosenweg_basis/rosenweg_basis.dart';

/// Antwortet wie Haupt-API und Telefonanlage, mit erfundenen Daten (keine
/// echten Personen, keine echten Karten). Fuer Tests, Bildschirmfotos und
/// Entwicklung ohne Server (--dart-define=RW_ATTRAPPE=1).
///
/// Karten zum Ausprobieren (Nummer eintippen):
///   04A1B2C3D4E5F6  zugeordnet (Erika Muster), Inventarnummer 0042
///   04112233445566  frei, Inventarnummer 0107
///   0A0B0C0D        unbekannt (nicht in UniFi Access)
class Attrappe implements Transport {
  Attrappe({this.gruppen = const ['technik']});

  /// Gruppen der angemeldeten Person (Tests: ohne Technik = keine Berechtigung).
  final List<String> gruppen;

  /// Alle Anfragen in der Reihenfolge (fuer Tests).
  final anfragen = <({String methode, String pfad, Object? koerper})>[];

  /// Naechste Antwort fuer einen Pfad erzwingen (Tests).
  final erzwungen = <String, ApiAntwort>{};

  final _karten = <String, Map<String, dynamic>>{
    '04A1B2C3D4E5F6': {'display_id': '0042', 'besitzer': 'erika.muster@example.org'},
    '04112233445566': {'display_id': '0107', 'besitzer': null},
  };

  static const _personen = [
    {'name': 'Erika Muster', 'email': 'erika.muster@example.org'},
    {'name': 'Max Beispiel', 'email': 'max.beispiel@example.org'},
    {'name': 'Lea Probe', 'email': 'lea.probe@example.org'},
  ];

  final _protokoll = <Map<String, dynamic>>[
    {'id': 3, 'aktion': 'admin-mit-karte', 'display_id': '0042', 'ziel_email': 'erika.muster@example.org', 'ziel_name': 'Erika Muster',
     'durch': 'karte:as_beispiel', 'erfolg': true, 'detail': 'Telefonanlage: geändert · App Rosenweg Karten', 'created_at': '2026-11-27T09:12:00Z'},
    {'id': 2, 'aktion': 'zuordnen', 'display_id': '0042', 'nfc_id': '04A1B2C3D4E5F6', 'ziel_email': 'erika.muster@example.org',
     'ziel_name': 'Erika Muster', 'durch': 'Rosenweg Karten (Max Beispiel)', 'erfolg': true, 'created_at': '2026-11-26T16:40:00Z'},
    {'id': 1, 'aktion': 'entziehen', 'display_id': '0099', 'ziel_name': 'Lea Probe', 'durch': 'Telefon 2098 (Max Beispiel)',
     'erfolg': false, 'detail': '502 UniFi Access nicht erreichbar', 'created_at': '2026-11-25T08:03:00Z'},
  ];

  final _sitzungen = <Map<String, dynamic>>[
    {'id': 'as_aaaaaaaaaaaaaaaa', 'email': 'erika.muster@example.org', 'name': 'Erika Muster', 'app_name': 'Rosenweg Salz',
     'geraet_name': 'Pixel 8', 'plattform': 'android', 'art': 'karte', 'karte_anzeige': '0042',
     'created_at': '2026-11-20T10:00:00Z', 'last_used_at': '2026-11-27T07:30:00Z', 'expires_at': '2026-12-20T10:00:00Z'},
    {'id': 'as_bbbbbbbbbbbbbbbb', 'email': 'max.beispiel@example.org', 'name': 'Max Beispiel', 'app_name': 'Rosenweg Profil',
     'geraet_name': 'Laptop', 'plattform': 'windows', 'art': 'handy',
     'created_at': '2026-11-22T18:00:00Z', 'last_used_at': '2026-11-26T21:15:00Z', 'expires_at': '2026-12-22T18:00:00Z'},
  ];

  Map<String, dynamic> _info(String uid) {
    final k = _karten[uid];
    if (k == null) return {'found': false, 'assigned': false, 'uid': uid};
    final b = k['besitzer'] as String?;
    final p = b == null ? null : _personen.firstWhere((x) => x['email'] == b);
    return {
      'found': true,
      'assigned': b != null,
      'status': b == null ? 'UNASSIGNED' : 'ASSIGNED',
      'display_id': k['display_id'],
      'access_name': p?['name'],
      'person': p,
      'uid': uid,
    };
  }

  @override
  Future<ApiAntwort> senden(String methode, String pfad, {Object? koerper}) async {
    anfragen.add((methode: methode, pfad: pfad, koerper: koerper));
    final z = erzwungen.remove('$methode $pfad');
    if (z != null) return z;
    final k = koerper is Map ? koerper : const {};
    final uid = '${k['uid'] ?? ''}'.toUpperCase();
    switch ('$methode ${pfad.split('?').first}') {
      case 'GET /api/auth/me':
        return (status: 200, json: {
          'user': {'id': 7, 'name': 'Max Beispiel', 'email': 'max.beispiel@example.org', 'groups': gruppen, 'permissions': <String, String>{}},
        });
      case 'GET /api/me/bestaetigungen':
        return (status: 200, json: {'offen': <Object>[]});
      case 'GET /api/karten-protokoll':
        return (status: 200, json: {'tage': 30, 'eintraege': _protokoll});
      case 'GET /api/apps/sitzungen':
        return (status: 200, json: {'sitzungen': _sitzungen, 'alle': true});
      case 'POST /api/apps/sitzungen/alle-abmelden':
        final n = _sitzungen.where((s) => s['email'] == k['email']).length;
        _sitzungen.removeWhere((s) => s['email'] == k['email']);
        return (status: 200, json: {'ok': true, 'abgemeldet': n});
      case 'GET /api/telefon/karten/personen':
        return (status: 200, json: {'personen': _personen});
      case 'POST /api/telefon/karten/info':
        return (status: 200, json: _info(uid));
      case 'POST /api/telefon/karten/zuordnen':
        final email = '${k['email']}';
        final karte = _karten.putIfAbsent(uid, () => {'display_id': '0${200 + _karten.length}', 'besitzer': null});
        if (karte['besitzer'] == email) return (status: 200, json: {'ok': true, 'schon': true});
        if (karte['besitzer'] != null) return (status: 409, json: {'error': 'Die Karte gehört bereits jemand anderem. Zuerst entziehen.'});
        karte['besitzer'] = email;
        return (status: 200, json: {'ok': true, 'display_id': karte['display_id']});
      case 'POST /api/telefon/karten/entziehen':
        final karte = _karten[uid];
        if (karte == null) return (status: 404, json: {'error': 'Karte nicht bekannt'});
        karte['besitzer'] = null;
        return (status: 200, json: {'ok': true});
      case 'POST /api/telefon/karten/loeschen':
        if (_karten.remove(uid) == null) return (status: 404, json: {'error': 'Karte nicht bekannt'});
        return (status: 200, json: {'ok': true});
    }
    if (methode == 'DELETE' && pfad.startsWith('/api/apps/sitzungen/')) {
      final id = Uri.decodeComponent(pfad.split('/').last);
      _sitzungen.removeWhere((s) => s['id'] == id);
      return (status: 200, json: {'ok': true});
    }
    return (status: 404, json: {'error': 'Nicht gefunden: $methode $pfad'});
  }
}
