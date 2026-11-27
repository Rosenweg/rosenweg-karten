// SPDX-License-Identifier: GPL-3.0-or-later
//
// Bildschirmfotos mit den Vorfuehrdaten (Attrappe), nur auf Wunsch:
//   RW_FOTO=$HOME/Downloads flutter test test/bildschirmfoto_test.dart
// Laedt Roboto und die Material-Symbole aus dem Flutter-SDK, sonst zeigen
// Widget-Tests nur Kaesten. Muster: rosenweg-profil.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rosenweg_basis/rosenweg_basis.dart';
import 'package:rosenweg_karten/app.dart';
import 'package:rosenweg_karten/attrappe.dart';
import 'package:rosenweg_karten/karten_api.dart';
import 'package:rosenweg_karten/seiten/haupt.dart';

import 'test_leser.dart';

final ziel = Platform.environment['RW_FOTO'];

Future<void> schriften() async {
  final sdk = File(Platform.resolvedExecutable).parent.parent.parent.parent.parent.parent.path;
  final ordner = ['$sdk/artifacts/material_fonts', '${Platform.environment['FLUTTER_ROOT']}/bin/cache/artifacts/material_fonts']
      .map(Directory.new)
      .firstWhere((d) => d.existsSync(), orElse: () => Directory('/opt/homebrew/share/flutter/bin/cache/artifacts/material_fonts'));
  Future<void> laden(String familie, List<String> dateien) async {
    final l = FontLoader(familie);
    for (final d in dateien) {
      l.addFont(Future.value(ByteData.sublistView(File('${ordner.path}/$d').readAsBytesSync())));
    }
    await l.load();
  }

  await laden('Roboto', ['Roboto-Regular.ttf', 'Roboto-Medium.ttf', 'Roboto-Bold.ttf']);
  await laden('MaterialIcons', ['MaterialIcons-Regular.otf']);
}

Future<void> foto(WidgetTester t, String datei) async {
  final rb = t.renderObject<RenderRepaintBoundary>(find.byType(RepaintBoundary).first);
  final bild = await t.runAsync(() => rb.toImage(pixelRatio: 2));
  final png = await t.runAsync(() => bild!.toByteData(format: ui.ImageByteFormat.png));
  File('$ziel/$datei').writeAsBytesSync(png!.buffer.asUint8List());
}

void main() {
  setUpAll(() async {
    Protokoll.nurKonsole = true;
    if (ziel != null) await schriften();
  });

  Future<TestLeser> zeigen(WidgetTester t, Size groesse, {Phase? phase}) async {
    t.view.physicalSize = groesse * 2;
    t.view.devicePixelRatio = 2;
    addTearDown(t.view.reset);
    await t.pumpWidget(const SizedBox());
    final a = Attrappe();
    final l = TestLeser();
    final s = RosenwegSitzung(appAngaben, transport: a);
    await s.starten();
    if (phase != null) s.phase = phase;
    await t.pumpWidget(RepaintBoundary(
        child: RosenwegRahmen(sitzung: s, inhalt: (_) => Haupt(sitzung: s, kartenApi: KartenApi(a), leser: l))));
    await t.runAsync(() => precacheImage(appAngaben.logo!, t.element(find.byType(MaterialApp))));
    await t.runAsync(() => precacheImage(const AssetImage('assets/logo/rosenweg-karten-klein.png'), t.element(find.byType(MaterialApp))));
    await t.pumpAndSettle();
    return l;
  }

  Future<void> auflegen(WidgetTester t, TestLeser l, String uid) async {
    l.auflegen(uid);
    await t.pump();
    await t.pumpAndSettle();
  }

  testWidgets('Fotos', (t) async {
    var l = await zeigen(t, const Size(412, 860));
    await foto(t, 'rosenweg-karten-handy-leer.png');
    await auflegen(t, l, '04A1B2C3D4E5F6');
    await foto(t, 'rosenweg-karten-handy.png');
    // Station (Linux, USB-Leser): eigener Satz unter dem Leser
    RosenwegPlattform.plattformFuerTests = TargetPlatform.linux;
    addTearDown(() => RosenwegPlattform.plattformFuerTests = null);
    l = await zeigen(t, const Size(1400, 860));
    await auflegen(t, l, '04112233445566');
    await foto(t, 'rosenweg-karten-station.png');
    await t.tap(find.text('Protokoll'));
    await t.pumpAndSettle();
    await foto(t, 'rosenweg-karten-protokoll.png');
    await t.tap(find.text('Anmeldungen'));
    await t.pumpAndSettle();
    await foto(t, 'rosenweg-karten-anmeldungen.png');
    RosenwegPlattform.plattformFuerTests = null;
    await zeigen(t, const Size(412, 860), phase: Phase.abgemeldet);
    await foto(t, 'rosenweg-karten-anmelden.png');
    await t.pumpWidget(const SizedBox());
  }, skip: ziel == null);
}
