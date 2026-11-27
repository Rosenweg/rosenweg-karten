// SPDX-License-Identifier: GPL-3.0-or-later
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rosenweg_basis/rosenweg_basis.dart';
import 'package:rosenweg_karten/app.dart';
import 'package:rosenweg_karten/attrappe.dart';
import 'package:rosenweg_karten/daten.dart';
import 'package:rosenweg_karten/karten_api.dart';
import 'package:rosenweg_karten/seiten/haupt.dart';

import 'test_leser.dart';

void main() {
  setUpAll(() => Protokoll.nurKonsole = true);

  Future<({Attrappe a, TestLeser l})> zeigen(WidgetTester t, Size groesse, {List<String> gruppen = const ['technik']}) async {
    t.view.physicalSize = groesse;
    t.view.devicePixelRatio = 1;
    addTearDown(t.view.reset);
    final a = Attrappe(gruppen: gruppen);
    final l = TestLeser();
    final s = RosenwegSitzung(appAngaben, transport: a);
    await s.starten();
    await t.pumpWidget(RosenwegRahmen(sitzung: s, inhalt: (_) => Haupt(sitzung: s, kartenApi: KartenApi(a), leser: l)));
    await t.pumpAndSettle();
    return (a: a, l: l);
  }

  Future<void> auflegen(WidgetTester t, TestLeser l, String uid) async {
    l.auflegen(uid);
    await t.pump();
    await t.pumpAndSettle();
  }

  testWidgets('Karte auflegen: zugeordnet, Inhaber und Nummer, nur „Entziehen“', (t) async {
    final x = await zeigen(t, const Size(412, 900));
    expect(find.text('Karte an die Rückseite des Handys halten.'), findsOneWidget);
    await auflegen(t, x.l, '04A1B2C3D4E5F6');
    expect(find.text('Erika Muster'), findsOneWidget);
    expect(find.text('Inventarnummer 0042'), findsOneWidget);
    expect(find.byKey(const Key('entziehen')), findsOneWidget);
    expect(find.byKey(const Key('zuordnen')), findsNothing);
    expect(find.byKey(const Key('loeschen')), findsNothing);
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Unbekannte Karte einer Person zuordnen, dann entziehen und löschen', (t) async {
    final x = await zeigen(t, const Size(412, 900));
    await auflegen(t, x.l, '0a:0b:0c:0d');
    expect(find.text('Unbekannte Karte'), findsOneWidget);
    expect(find.text('Nicht in UniFi Access'), findsOneWidget);

    await t.tap(find.byKey(const Key('zuordnen')));
    await t.pumpAndSettle();
    await t.enterText(find.byType(TextField).last, 'lea');
    await t.pumpAndSettle();
    await t.tap(find.text('Lea Probe'));
    await t.pumpAndSettle();
    expect(find.text('Karte zuordnen?'), findsOneWidget);
    await t.tap(find.text('Zuordnen'));
    await t.pumpAndSettle();
    expect(find.textContaining('Zugeordnet. Die Türleser'), findsOneWidget);
    expect(find.text('Lea Probe'), findsOneWidget);
    final z = x.a.anfragen.firstWhere((r) => r.pfad == '/api/telefon/karten/zuordnen');
    expect(z.koerper, {'uid': '0A0B0C0D', 'email': 'lea.probe@example.org'});

    await t.tap(find.byKey(const Key('entziehen')));
    await t.pumpAndSettle();
    await t.tap(find.widgetWithText(FilledButton, 'Entziehen'));
    await t.pumpAndSettle();
    expect(find.text('Entzogen.'), findsOneWidget);
    expect(find.text('Frei – keiner Person zugeordnet'), findsOneWidget);

    await t.tap(find.byKey(const Key('loeschen')));
    await t.pumpAndSettle();
    await t.tap(find.widgetWithText(FilledButton, 'Löschen'));
    await t.pumpAndSettle();
    expect(find.text('Aus dem Bestand gelöscht.'), findsOneWidget);
    expect(find.text('Unbekannte Karte'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Mit der Karte heikle Aktion: 202 zeigt „Bestätigung per E-Mail nötig“', (t) async {
    final x = await zeigen(t, const Size(412, 900));
    await auflegen(t, x.l, '04A1B2C3D4E5F6');
    x.a.erzwungen['POST /api/telefon/karten/entziehen'] = (
      status: 202,
      json: {
        'bestaetigung': 'email',
        'id': 'kb_1',
        'email': 'm…@example.org',
        'ablauf': '2026-11-27T18:15:00Z',
        'hinweis': 'Bitte bestätige in der E-Mail, die wir dir an m…@example.org geschickt haben. Der Link gilt 15 Minuten.',
      },
    );
    await t.tap(find.byKey(const Key('entziehen')));
    await t.pumpAndSettle();
    await t.tap(find.widgetWithText(FilledButton, 'Entziehen'));
    await t.pumpAndSettle();
    expect(find.text('Bestätigung per E-Mail nötig'), findsOneWidget);
    await t.tap(find.text('Verstanden'));
    await t.pumpAndSettle();
    expect(find.textContaining('m…@example.org'), findsOneWidget);
    // nichts passiert: Karte gehoert weiter Erika
    expect(find.text('Erika Muster'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Ohne Technik/Präsident: klare Meldung statt Kartenleser', (t) async {
    await zeigen(t, const Size(412, 900), gruppen: const ['stweg3-eigentuemer']);
    expect(find.text('Die Kartenverwaltung ist nur für Technik und Präsident.'), findsOneWidget);
    expect(find.byKey(const Key('leser')), findsNothing);
  });

  testWidgets('Station: Leiste links, Leser und Ergebnis nebeneinander', (t) async {
    final x = await zeigen(t, const Size(1280, 800));
    expect(find.byType(NavigationRail), findsOneWidget);
    await auflegen(t, x.l, '04112233445566');
    expect(find.text('Inventarnummer 0107'), findsOneWidget);
    expect(find.byKey(const Key('loeschen')), findsOneWidget);
    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Protokoll und Anmeldungen: Liste, Filter, alle Apps einer Person abmelden', (t) async {
    final x = await zeigen(t, const Size(412, 900));
    await t.tap(find.text('Protokoll'));
    await t.pumpAndSettle();
    expect(find.textContaining('Admin mit Karte'), findsWidgets);
    await t.tap(find.widgetWithText(FilterChip, 'Fehlgeschlagen'));
    await t.pumpAndSettle();
    expect(find.textContaining('Entzogen'), findsOneWidget);
    expect(find.textContaining('Zugeordnet'), findsNothing);

    await t.tap(find.text('Anmeldungen'));
    await t.pumpAndSettle();
    expect(find.text('Erika Muster'), findsOneWidget);
    await t.tap(find.text('Erika Muster'));
    await t.pumpAndSettle();
    await t.tap(find.text('Alle Apps dieser Person abmelden'));
    await t.pumpAndSettle();
    await t.tap(find.widgetWithText(FilledButton, 'Alle abmelden'));
    await t.pumpAndSettle();
    expect(find.text('Erika Muster'), findsNothing);
    expect(x.a.anfragen.any((r) => r.pfad == '/api/apps/sitzungen/alle-abmelden'), isTrue);
    await t.pumpWidget(const SizedBox());
  });

  test('Geteiltes Telefon: Karten-Methoden von Rosenweg Gerät wie die alte App', () async {
    final rufe = <String>[];
    final tr = GeraetKartenTransport(rufen: (m, {arg, extras}) async {
      rufe.add('$m $arg ${extras ?? ''}');
      if (m == 'karten_info') return {'json': '{"found":true,"assigned":false,"display_id":"0107"}'};
      if (m == 'karten_zuordnen') return {'nicht_berechtigt': true};
      return {'fehler': 'Keine Verbindung zur Anlage'};
    });
    final api = KartenApi(tr);
    final k = await api.nachschlagen('04112233445566');
    expect(k.inventarnummer, '0107');
    expect(k.loeschbar, isTrue);
    await expectLater(api.zuordnen('04112233445566', 'lea.probe@example.org'), throwsA(isA<KeineBerechtigung>()));
    await expectLater(api.entziehen('04112233445566'), throwsA(isA<ApiFehler>().having((e) => e.text, 'text', 'Keine Verbindung zur Anlage')));
    expect(rufe, [
      'karten_info 04112233445566 ',
      'karten_zuordnen 04112233445566 {email: lea.probe@example.org}',
      'karten_entziehen 04112233445566 ',
    ]);
    // leeres Bundle: Rosenweg Geraet zu alt
    expect(() => geraetKartenAntwort(const {}), throwsA(isA<GeraetFehler>()));
  });

  test('Karte aus der Antwort der Anlage', () {
    final k = Karte.aus('04AA', {'found': true, 'assigned': false, 'status': 'UNASSIGNED', 'display_id': null, 'access_name': null});
    expect(k.zustandText, 'Frei – keiner Person zugeordnet');
    expect(k.nummerText, 'Ohne Inventarnummer');
    expect(k.loeschbar, isTrue);
    expect(Karte.aus('04AA', {'found': false}).loeschbar, isFalse);
  });

  test('Kennung, Ordner und Logo passen zusammen', () {
    expect(appAngaben.kennung, 'ch.rosenweg4303.karten');
    expect(File('assets/logo/${appAngaben.ordner}-512.png').existsSync(), isTrue);
    expect(File('assets/logo/${appAngaben.ordner}-klein.png').existsSync(), isTrue);
  });

  test('401 der Anlage: Grund auf den Schirm, kein stilles Abmelden (wie 1.6.3)', () async {
    final a = Attrappe();
    a.erzwungen['POST /api/telefon/karten/info'] = (status: 401, json: {'error': 'Geraet nicht berechtigt'});
    await expectLater(
      KartenApi(a).nachschlagen('04AABBCC'),
      throwsA(isA<ApiFehler>().having((e) => e.text, 'text', contains('Geraet nicht berechtigt')).having((e) => e is NichtAngemeldet, 'abmelden', isFalse)),
    );
  });
}
