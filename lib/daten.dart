// SPDX-License-Identifier: GPL-3.0-or-later
// Was die Anlage zu Karten, Personen, Protokoll und Anmeldungen liefert.

String? _text(Object? v) {
  if (v == null) return null;
  final s = '$v'.trim();
  return s.isEmpty || s == 'null' ? null : s;
}

/// Antwort von „Karte nachschlagen“ (Telefonanlage /api/telefon/karten/info,
/// dahinter karteNachschlagen der Haupt-API und UniFi Access).
class Karte {
  const Karte({
    required this.uid,
    required this.bekannt,
    required this.zugeordnet,
    this.status,
    this.inventarnummer,
    this.accessName,
    this.personName,
    this.personEmail,
  });

  final String uid;

  /// In UniFi Access vorhanden.
  final bool bekannt;

  /// Einer Person zugeordnet (meldet an, oeffnet Tueren).
  final bool zugeordnet;
  final String? status;

  /// `display_id` in UniFi Access (aufgedruckte Nummer).
  final String? inventarnummer;
  final String? accessName;
  final String? personName;
  final String? personEmail;

  factory Karte.aus(String uid, Map<String, dynamic> j) {
    final p = j['person'];
    return Karte(
      uid: _text(j['uid']) ?? uid,
      bekannt: j['found'] == true,
      zugeordnet: j['assigned'] == true,
      status: _text(j['status']),
      inventarnummer: _text(j['display_id']),
      accessName: _text(j['access_name']),
      personName: p is Map ? _text(p['name']) : null,
      personEmail: p is Map ? _text(p['email']) : null,
    );
  }

  /// Hat einen Inhaber (Person im Portal oder Name in UniFi Access).
  bool get hatInhaber => zugeordnet || personEmail != null || personName != null || accessName != null;

  /// Wem sie gehoert, als Satz.
  String get inhaber {
    if (!bekannt) return 'Unbekannte Karte';
    return personName ?? accessName ?? personEmail ?? 'Freie Karte';
  }

  String get nummerText {
    if (!bekannt) return 'Nicht in UniFi Access';
    return inventarnummer == null ? 'Ohne Inventarnummer' : 'Inventarnummer $inventarnummer';
  }

  String get zustandText {
    if (!bekannt) return 'Beim Zuordnen wird sie in UniFi Access angelegt.';
    if (zugeordnet) return 'Zugeordnet – meldet an und öffnet Türen';
    final s = status;
    if (s == null || s.toLowerCase() == 'unassigned') return 'Frei – keiner Person zugeordnet';
    return 'Status: $s';
  }

  /// Name fuer Rueckfragen („Karte 0042“ bzw. die UID).
  String get kurzName => inventarnummer ?? uid;

  /// Aus dem Bestand loeschen geht nur bei bekannten Karten ohne Inhaber.
  bool get loeschbar => bekannt && !hatInhaber;
}

/// Person fuer „Einer Person zuordnen“.
class Person {
  const Person({required this.name, required this.email});
  final String name;
  final String email;

  factory Person.aus(Map<String, dynamic> j) =>
      Person(name: _text(j['name']) ?? _text(j['email']) ?? '', email: (_text(j['email']) ?? '').toLowerCase());

  bool passt(String suche) {
    final q = suche.trim().toLowerCase();
    return q.isEmpty || name.toLowerCase().contains(q) || email.contains(q);
  }
}

/// Eintrag im Kartenprotokoll (GET /api/karten-protokoll).
class ProtokollEintrag {
  const ProtokollEintrag({
    required this.id,
    required this.aktion,
    required this.zeit,
    required this.erfolg,
    this.inventarnummer,
    this.nfcId,
    this.zielName,
    this.zielEmail,
    this.durch,
    this.detail,
  });

  final String id;
  final String aktion;
  final DateTime? zeit;
  final bool erfolg;
  final String? inventarnummer;
  final String? nfcId;
  final String? zielName;
  final String? zielEmail;
  final String? durch;
  final String? detail;

  factory ProtokollEintrag.aus(Map<String, dynamic> j) => ProtokollEintrag(
        id: '${j['id']}',
        aktion: _text(j['aktion']) ?? '',
        zeit: DateTime.tryParse('${j['created_at']}')?.toLocal(),
        erfolg: j['erfolg'] != false,
        inventarnummer: _text(j['display_id']),
        nfcId: _text(j['nfc_id']),
        zielName: _text(j['ziel_name']),
        zielEmail: _text(j['ziel_email']),
        durch: _text(j['durch']),
        detail: _text(j['detail']),
      );

  /// Aktion als deutsches Wort.
  String get aktionText => aktionTexte[aktion] ?? aktion;

  String get wen => zielName ?? zielEmail ?? '';

  String get suchText => [aktionText, inventarnummer, nfcId, zielName, zielEmail, durch, detail].whereType<String>().join(' ');

  static const aktionTexte = {
    'zuordnen': 'Zugeordnet',
    'entziehen': 'Entzogen',
    'loeschen': 'Gelöscht',
    'anmelden': 'Anmeldung (Telefon)',
    'app-anmeldung': 'Anmeldung (App)',
    'app-abmeldung': 'Abmeldung (App)',
    'admin-mit-karte': 'Admin mit Karte',
    'bestaetigung-angefragt': 'Bestätigung angefragt',
    'bestaetigt': 'Bestätigt',
    'abgelehnt': 'Abgelehnt',
  };
}

/// Karten-, QR- oder Handy-Anmeldung einer App (GET /api/apps/sitzungen).
class AppSitzung {
  const AppSitzung({
    required this.id,
    required this.email,
    required this.name,
    required this.app,
    required this.geraet,
    required this.art,
    this.plattform,
    this.karte,
    this.erstellt,
    this.zuletzt,
    this.ablauf,
    this.widerrufen,
  });

  final String id;
  final String email;
  final String name;
  final String app;
  final String geraet;

  /// karte, qr oder handy.
  final String art;
  final String? plattform;
  final String? karte;
  final DateTime? erstellt;
  final DateTime? zuletzt;
  final DateTime? ablauf;
  final DateTime? widerrufen;

  factory AppSitzung.aus(Map<String, dynamic> j) {
    DateTime? zeit(String f) => DateTime.tryParse('${j[f]}')?.toLocal();
    return AppSitzung(
      id: '${j['id']}',
      email: (_text(j['email']) ?? '').toLowerCase(),
      name: _text(j['name']) ?? _text(j['email']) ?? '',
      app: _text(j['app_name']) ?? _text(j['app']) ?? 'App',
      geraet: _text(j['geraet_name']) ?? 'Gerät',
      art: _text(j['art']) ?? 'karte',
      plattform: _text(j['plattform']),
      karte: _text(j['karte_anzeige']),
      erstellt: zeit('created_at'),
      zuletzt: zeit('last_used_at'),
      ablauf: zeit('expires_at'),
      widerrufen: zeit('revoked_at'),
    );
  }

  String get artText => switch (art) {
        'qr' => 'QR-Code',
        'handy' => 'mit dem Handy',
        _ => karte == null ? 'Karte' : 'Karte $karte',
      };

  String get suchText => '$name $email $app $geraet ${karte ?? ''}';
}

/// Offene Bestaetigung per E-Mail (GET /api/me/bestaetigungen).
class OffeneBestaetigung {
  const OffeneBestaetigung({required this.id, required this.text, this.ablauf});
  final String id;
  final String text;
  final DateTime? ablauf;

  factory OffeneBestaetigung.aus(Map<String, dynamic> j) => OffeneBestaetigung(
        id: '${j['id']}',
        text: _text(j['text']) ?? 'Aktion',
        ablauf: DateTime.tryParse('${j['ablauf']}')?.toLocal(),
      );
}

/// 27.11.2026 14:05
String wannText(DateTime? z) {
  if (z == null) return '–';
  String zwei(int n) => n.toString().padLeft(2, '0');
  return '${zwei(z.day)}.${zwei(z.month)}.${z.year} ${zwei(z.hour)}:${zwei(z.minute)}';
}
