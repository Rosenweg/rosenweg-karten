package ch.rosenweg4303.karten;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import ch.rosenweg4303.cloud.RosenwegAnmeldeSchirm;
import ch.rosenweg4303.cloud.RosenwegAnmeldung;
import ch.rosenweg4303.anmeldeschirm.Kartenleser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rosenweg Karten: NFC-Karte (UniFi Access) hinten ans Telefon halten -> wem gehoert sie,
 * Inventarnummer, Status; "Einer Person zuordnen", "Entziehen" oder (entzogen) "Aus dem Bestand loeschen".
 *
 * Nur fuer Technik/Praesident. Mit Rosenweg Geraet (geteiltes Telefon) geht alles ueber dessen
 * AnlageProvider; ohne Geraet (eigenes Handy, Stefan 25.11.2026) meldet sich die App mit dem
 * Rosenweg-Konto an (Cloud-Modul, RosenwegAnmeldung) und fragt die Anlage direkt.
 * Mit Geraet: der AnlageProvider (gleiche Signatur) prueft die angemeldete Person
 * prueft; die Anlage prueft nochmals.
 */
public class KartenActivity extends Activity {
	private static final String TAG = "RosenwegKarten";
	private static final Uri ANLAGE = Uri.parse("content://ch.rosenweg4303.telefon.anlage");
	/** Reader-Modus und UID wie Sperrbildschirm und Anmeldeschirm (commons-rosenweg, anmeldeschirm). */
	private static final int LESER_FLAGS = Kartenleser.FLAGS;
	private final Kartenleser.Entpreller entpreller = new Kartenleser.Entpreller(3000);

	private final ExecutorService netz = Executors.newSingleThreadExecutor();
	private LinearLayout inhalt;
	private TextView hinweis, meldung, inhaber, nummer, zustand, uidText;
	private LinearLayout kartenFeld;
	private Button zuordnen, entziehen, loeschen;
	private boolean berechtigt;     // erst nach der ersten Antwort des Providers
	private volatile boolean beschaeftigt;
	private String uid;
	private JSONObject karte;
	private List<JSONObject> personen;

	/** Antwort des Providers: json oder fehler. */
	private static final class Antwort {
		JSONObject json;
		String fehler;
		boolean nichtBerechtigt;
		boolean abgemeldet;   // ohne Geraet: keine (gueltige) Rosenweg-Anmeldung
	}

	private static final String ANLAGE_WEB = "https://pbx.rosenweg4303.ch";

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		setContentView(baue());
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Eigenes Handy ohne Anmeldung: gemeinsamer Anmeldeschirm (commons-rosenweg, wie der
		// Sperrbildschirm der geteilten Telefone: Rosenweg-Karte, Rosenweg-Konto oder QR-Code)
		if (RosenwegAnmeldeSchirm.zeigenFallsNoetig(this)) {
			meldungIstAnmelden = true;
			return;
		}
		if (meldungIstAnmelden) {
			meldungIstAnmelden = false;
			setContentView(baue());
		}
		leserEin();
		// Berechtigung pruefen und Personenliste vorladen
		ausfuehren(() -> rufe("karten_personen", "personen", null), a -> {
			if (a.abgemeldet) {
				zeigeAnmelden();
				return;
			}
			if (a.nichtBerechtigt) {
				nichtBerechtigt();
				return;
			}
			if (a.json != null) {
				if (meldungIstNichtBerechtigt) {   // inzwischen berechtigt (andere Person angemeldet)
					meldungIstNichtBerechtigt = false;
					setContentView(baue());
				}
				berechtigt = true;
				personen = liste(a.json.optJSONArray("personen"));
				hinweis.setText("Karte hinten ans Telefon halten.");
				meldung.setVisibility(View.GONE);
			} else {
				zeigeFehler(a.fehler);
			}
		});
	}

	@Override
	protected void onPause() {
		NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
		if (nfc != null) {
			try {
				nfc.disableReaderMode(this);
			} catch (Exception e) {
				Log.w(TAG, "Leser aus: " + e);
			}
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		netz.shutdownNow();
		super.onDestroy();
	}

	// ─── NFC ────────────────────────────────────────────────────────────────

	private void leserEin() {
		NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
		if (nfc == null) {
			zeigeFehler("Dieses Telefon hat kein NFC.");
			return;
		}
		if (meldungIstNichtBerechtigt) return;
		if (!nfc.isEnabled()) zeigeFehler("NFC ist ausgeschaltet (Einstellungen › Verbindungen › NFC).");
		try {
			nfc.enableReaderMode(this, this::karteGelesen, LESER_FLAGS, null);
		} catch (Exception e) {
			Log.w(TAG, "Leser: " + e);
		}
	}

	private void karteGelesen(Tag tag) {
		String gelesen = Kartenleser.uid(tag);
		if (gelesen == null || !entpreller.neu(gelesen)) return;   // mehrfach gemeldet
		runOnUiThread(() -> nachschlagen(gelesen));
	}

	private void nachschlagen(String neueUid) {
		if (!berechtigt && meldungIstNichtBerechtigt) return;
		uid = neueUid;
		karte = null;
		kartenFeld.setVisibility(View.VISIBLE);
		inhaber.setText("…");
		nummer.setText("");
		zustand.setText("");
		uidText.setText("UID " + neueUid);
		zuordnen.setVisibility(View.GONE);
		entziehen.setVisibility(View.GONE);
		loeschen.setVisibility(View.GONE);
		meldung.setVisibility(View.GONE);
		hinweis.setText("Karte wird nachgeschlagen …");
		ausfuehren(() -> rufe("karten_info", neueUid, null), a -> {
			if (!neueUid.equals(uid)) return;   // inzwischen andere Karte
			hinweis.setText("Karte hinten ans Telefon halten.");
			if (a.nichtBerechtigt) {
				nichtBerechtigt();
				return;
			}
			if (a.json == null) {
				zeigeFehler(a.fehler);
				inhaber.setText("–");
				return;
			}
			zeigeKarte(a.json);
		});
	}

	private void zeigeKarte(JSONObject k) {
		karte = k;
		boolean bekannt = k.optBoolean("found");
		JSONObject p = k.optJSONObject("person");
		String nr = k.optString("display_id", "");
		if (!bekannt) {
			inhaber.setText("Unbekannte Karte");
			nummer.setText("Nicht in UniFi Access");
			zustand.setText("Beim Zuordnen wird sie in UniFi Access angelegt.");
		} else {
			String besitzer = k.isNull("access_name") ? "" : k.optString("access_name", "");
			String name = p != null ? p.optString("name", "") : besitzer;
			String email = p != null ? p.optString("email", "") : "";
			inhaber.setText(name.isEmpty() ? (email.isEmpty() ? "Niemand" : email) : name);
			nummer.setText(nr.isEmpty() || "null".equals(nr) ? "Ohne Inventarnummer" : "Inventarnummer " + nr);
			String st = k.optString("status", "");
			String text = k.optBoolean("assigned") ? "Zugeordnet — meldet an und öffnet Türen"
					: "unassigned".equalsIgnoreCase(st) || st.isEmpty() || "null".equals(st) ? "Frei — keiner Person zugeordnet"
					: "Status: " + st;
			if (p != null && !email.isEmpty() && !name.isEmpty()) text += "\n" + email;
			zustand.setText(text);
		}
		zuordnen.setVisibility(View.VISIBLE);
		boolean hatBesitzer = p != null || k.optBoolean("assigned")
				|| !(k.isNull("access_name") || k.optString("access_name", "").isEmpty());
		entziehen.setVisibility(bekannt && hatBesitzer ? View.VISIBLE : View.GONE);
		// Loeschen nur fuer bekannte, entzogene Karten (zugeordnete zuerst entziehen)
		loeschen.setVisibility(bekannt && !hatBesitzer ? View.VISIBLE : View.GONE);
	}

	// ─── Zuordnen / Entziehen ───────────────────────────────────────────────

	private void personWaehlen() {
		if (uid == null) return;
		if (personen == null || personen.isEmpty()) {
			zeigeFehler("Keine Personen geladen.");
			return;
		}
		LinearLayout f = new LinearLayout(this);
		f.setOrientation(LinearLayout.VERTICAL);
		f.setPadding(dp(16), dp(8), dp(16), 0);
		EditText suche = new EditText(this);
		suche.setHint("Name oder E-Mail suchen");
		suche.setSingleLine(true);
		f.addView(suche);
		ListView lv = new ListView(this);
		List<JSONObject> sichtbar = new ArrayList<>(personen);
		ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, new ArrayList<>()) {
			@Override
			public View getView(int pos, View alt, android.view.ViewGroup eltern) {
				View v = super.getView(pos, alt, eltern);
				JSONObject p = sichtbar.get(pos);
				((TextView) v.findViewById(android.R.id.text1)).setText(p.optString("name"));
				((TextView) v.findViewById(android.R.id.text2)).setText(p.optString("email"));
				return v;
			}
		};
		Runnable filtern = () -> {
			String q = suche.getText().toString().trim().toLowerCase(Locale.ROOT);
			sichtbar.clear();
			for (JSONObject p : personen) {
				if (q.isEmpty() || p.optString("name").toLowerCase(Locale.ROOT).contains(q)
						|| p.optString("email").toLowerCase(Locale.ROOT).contains(q)) sichtbar.add(p);
			}
			ad.clear();
			for (JSONObject p : sichtbar) ad.add(p.optString("name"));
			ad.notifyDataSetChanged();
		};
		filtern.run();
		lv.setAdapter(ad);
		f.addView(lv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380)));
		suche.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

			public void onTextChanged(CharSequence s, int a, int b, int c) {}

			public void afterTextChanged(Editable s) {
				filtern.run();
			}
		});
		AlertDialog d = new AlertDialog.Builder(this)
				.setTitle("Einer Person zuordnen")
				.setView(f)
				.setNegativeButton("Abbrechen", null)
				.show();
		final String kartenUid = uid;
		lv.setOnItemClickListener((x, v, pos, id) -> {
			JSONObject p = sichtbar.get(pos);
			d.dismiss();
			new AlertDialog.Builder(this)
					.setMessage("Karte " + kartenName() + " " + p.optString("name") + " zuordnen?\n\n"
							+ "Sie öffnet danach die Türen dieser Person und meldet sie an den geteilten Telefonen an.")
					.setPositiveButton("Zuordnen", (y, w) -> zuordnenAusfuehren(kartenUid, p.optString("email")))
					.setNegativeButton("Abbrechen", null)
					.show();
		});
	}

	private void zuordnenAusfuehren(String kartenUid, String email) {
		Bundle extras = new Bundle();
		extras.putString("email", email);
		hinweis.setText("Wird zugeordnet …");
		ausfuehren(() -> rufe("karten_zuordnen", kartenUid, extras), a -> {
			hinweis.setText("Karte hinten ans Telefon halten.");
			if (a.nichtBerechtigt) {
				nichtBerechtigt();
				return;
			}
			if (a.json == null) {
				zeigeFehler(a.fehler);
				return;
			}
			zeigeOk(a.json.optBoolean("schon") ? "War schon so zugeordnet."
					: "Zugeordnet. Die Türleser übernehmen das in etwa 30 Sekunden.");
			nachschlagenSpaeter(kartenUid);
		});
	}

	private void entziehenFragen() {
		if (uid == null) return;
		final String kartenUid = uid;
		new AlertDialog.Builder(this)
				.setTitle("Karte entziehen?")
				.setMessage("Karte " + kartenName() + " wird " + inhaber.getText() + " entzogen. Sie öffnet danach "
						+ "keine Tür mehr und meldet an keinem Telefon mehr an.")
				.setPositiveButton("Entziehen", (x, w) -> {
					hinweis.setText("Wird entzogen …");
					ausfuehren(() -> rufe("karten_entziehen", kartenUid, null), a -> {
						hinweis.setText("Karte hinten ans Telefon halten.");
						if (a.nichtBerechtigt) {
							nichtBerechtigt();
							return;
						}
						if (a.json == null) {
							zeigeFehler(a.fehler);
							return;
						}
						zeigeOk("Entzogen.");
						nachschlagenSpaeter(kartenUid);
					});
				})
				.setNegativeButton("Abbrechen", null)
				.show();
	}

	private void loeschenFragen() {
		if (uid == null) return;
		final String kartenUid = uid;
		new AlertDialog.Builder(this)
				.setTitle("Karte aus dem Bestand löschen?")
				.setMessage("Karte " + kartenName() + " wird aus UniFi Access gelöscht (defekt oder verloren). "
						+ "Sie lässt sich danach nur als neue Karte wieder zuordnen, mit neuer Inventarnummer.")
				.setPositiveButton("Löschen", (x, w) -> {
					hinweis.setText("Wird gelöscht …");
					ausfuehren(() -> rufe("karten_loeschen", kartenUid, null), a -> {
						hinweis.setText("Karte hinten ans Telefon halten.");
						if (a.nichtBerechtigt) {
							nichtBerechtigt();
							return;
						}
						if (a.json == null) {
							zeigeFehler(a.fehler);
							return;
						}
						zeigeOk("Aus dem Bestand gelöscht.");
						nachschlagenSpaeter(kartenUid);
					});
				})
				.setNegativeButton("Abbrechen", null)
				.show();
	}

	private void nachschlagenSpaeter(String kartenUid) {
		// Meldung stehen lassen, nur die Karte neu laden
		CharSequence m = meldung.getText();
		int farbe = meldung.getCurrentTextColor();
		ausfuehren(() -> rufe("karten_info", kartenUid, null), a -> {
			if (a.json != null && kartenUid.equals(uid)) zeigeKarte(a.json);
			meldung.setText(m);
			meldung.setTextColor(farbe);
			meldung.setVisibility(View.VISIBLE);
		});
	}

	private String kartenName() {
		String nr = karte == null ? "" : karte.optString("display_id", "");
		return nr.isEmpty() || "null".equals(nr) ? "…" + (uid == null ? "" : uid.substring(Math.max(0, uid.length() - 6))) : nr;
	}

	// ─── Provider ───────────────────────────────────────────────────────────

	private interface Arbeit {
		Antwort los();
	}

	private interface Fertig {
		void mit(Antwort a);
	}

	private void ausfuehren(Arbeit arbeit, Fertig fertig) {
		if (netz.isShutdown()) return;
		beschaeftigt = true;
		netz.execute(() -> {
			Antwort a = arbeit.los();
			runOnUiThread(() -> {
				beschaeftigt = false;
				if (!isFinishing()) fertig.mit(a);
			});
		});
	}

	/** Blockiert (der Provider geht ins Netz): nur im Hintergrund. */
	private Antwort rufe(String methode, String arg, Bundle extras) {
		if (!RosenwegAnmeldung.INSTANCE.geraetDa(this)) return rufeAnlage(methode, arg, extras);
		Antwort a = new Antwort();
		try {
			Bundle b = getContentResolver().call(ANLAGE, methode, arg, extras);
			if (b == null) {
				a.fehler = "Rosenweg Gerät antwortet nicht.";
			} else if (b.getBoolean("nicht_berechtigt")) {
				a.nichtBerechtigt = true;
			} else if (b.getString("json") != null) {
				a.json = new JSONObject(b.getString("json"));
			} else if (b.getString("fehler") != null) {
				a.fehler = b.getString("fehler");
			} else {
				// leeres Bundle: Rosenweg Geraet nicht eingerichtet oder zu alt (vor v186)
				a.fehler = "Rosenweg Gerät ist nicht eingerichtet oder zu alt (mindestens Version 186).";
			}
		} catch (SecurityException e) {
			a.fehler = "Rosenweg Gerät verweigert den Zugriff (andere Signatur?).";
		} catch (IllegalArgumentException e) {
			a.fehler = "Rosenweg Gerät ist nicht installiert.";
		} catch (Exception e) {
			Log.w(TAG, methode + ": " + e);
			a.fehler = "Fehler: " + e.getMessage();
		}
		return a;
	}

	/** Ohne Rosenweg Geraet: dieselben Anfragen direkt an die Anlage, mit der Rosenweg-Anmeldung. */
	private Antwort rufeAnlage(String methode, String arg, Bundle extras) {
		Antwort a = new Antwort();
		java.net.HttpURLConnection v = null;
		try {
			String token = RosenwegAnmeldung.INSTANCE.zugangsToken(this);
			if (token == null) {
				a.abgemeldet = true;
				return a;
			}
			String pfad;
			JSONObject koerper = null;
			switch (methode) {
				case "karten_personen": pfad = "/api/telefon/karten/personen"; break;
				case "karten_info": pfad = "/api/telefon/karten/info"; koerper = new JSONObject().put("uid", arg); break;
				case "karten_zuordnen": pfad = "/api/telefon/karten/zuordnen";
					koerper = new JSONObject().put("uid", arg).put("email", extras == null ? "" : extras.getString("email", "")); break;
				case "karten_entziehen": pfad = "/api/telefon/karten/entziehen"; koerper = new JSONObject().put("uid", arg); break;
				case "karten_loeschen": pfad = "/api/telefon/karten/loeschen"; koerper = new JSONObject().put("uid", arg); break;
				default: a.fehler = "Unbekannte Anfrage"; return a;
			}
			v = (java.net.HttpURLConnection) new java.net.URL(ANLAGE_WEB + pfad).openConnection();
			v.setConnectTimeout(10000);
			v.setReadTimeout(20000);
			v.setRequestProperty("Authorization", "Bearer " + token);
			v.setRequestProperty("Accept", "application/json");
			if (koerper != null) {
				v.setRequestMethod("POST");
				v.setDoOutput(true);
				v.setRequestProperty("Content-Type", "application/json");
				try (java.io.OutputStream o = v.getOutputStream()) { o.write(koerper.toString().getBytes("UTF-8")); }
			}
			int status = v.getResponseCode();
			java.io.InputStream in = status < 400 ? v.getInputStream() : v.getErrorStream();
			StringBuilder text = new StringBuilder();
			if (in != null) {
				try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"))) {
					for (String z; (z = r.readLine()) != null; ) text.append(z);
				}
			}
			JSONObject j = text.length() > 0 ? new JSONObject(text.toString()) : new JSONObject();
			if (status == 401) {
				RosenwegAnmeldung.INSTANCE.zugangUngueltig();
				a.abgemeldet = true;
			} else if (status == 403) {
				a.nichtBerechtigt = true;
			} else if (status == 200) {
				a.json = j;
			} else {
				a.fehler = j.optString("error", "Anlage antwortet mit " + status);
			}
		} catch (java.io.IOException e) {
			a.fehler = "Keine Verbindung zur Anlage.";
		} catch (Exception e) {
			Log.w(TAG, methode + ": " + e);
			a.fehler = "Fehler: " + e.getMessage();
		} finally {
			if (v != null) v.disconnect();
		}
		return a;
	}

	private static List<JSONObject> liste(JSONArray a) {
		List<JSONObject> l = new ArrayList<>();
		for (int i = 0; a != null && i < a.length(); i++) {
			JSONObject p = a.optJSONObject(i);
			if (p != null && !p.optString("email").isEmpty()) l.add(p);
		}
		return l;
	}

	// ─── Oberflaeche (ohne Layout-Dateien wie Rosenweg Geraet) ──────────────

	private boolean meldungIstNichtBerechtigt;
	private boolean meldungIstAnmelden;

	/**
	 * Ohne Rosenweg Geraet: Anmeldung ungueltig (401) oder abgemeldet - Anmeldung verwerfen und den
	 * gemeinsamen Anmeldeschirm zeigen (commons-rosenweg: Rosenweg-Karte, Rosenweg-Konto oder QR-Code).
	 */
	private void zeigeAnmelden() {
		meldungIstAnmelden = true;
		berechtigt = false;
		NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
		if (nfc != null) {
			try { nfc.disableReaderMode(this); } catch (Exception ignored) { }
		}
		RosenwegAnmeldung.INSTANCE.abmelden(this);
		RosenwegAnmeldeSchirm.zeigenFallsNoetig(this);
	}

	private void nichtBerechtigt() {
		berechtigt = false;
		meldungIstNichtBerechtigt = true;
		NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
		if (nfc != null) {
			try { nfc.disableReaderMode(this); } catch (Exception ignored) { }
		}
		inhalt.removeAllViews();
		TextView t = text(20, Color.parseColor("#FCA5A5"), true);
		t.setText("Nicht berechtigt");
		t.setPadding(0, dp(60), 0, dp(8));
		inhalt.addView(t);
		TextView u = text(15, Color.parseColor("#94A3B8"), false);
		u.setText("Die Kartenverwaltung ist nur für Technik und Präsident.");
		inhalt.addView(u);
		// Eigenes Handy: mit einer anderen Person anmelden (Abmelden in den Einstellungen)
		View platz = new View(this);
		inhalt.addView(platz, new LinearLayout.LayoutParams(1, 0, 1f));
		inhalt.addView(einstellungenZeile(Color.parseColor("#94A3B8")));
	}

	private void zeigeFehler(String t) {
		meldung.setText(t == null ? "Unbekannter Fehler" : t);
		meldung.setTextColor(Color.parseColor("#FCA5A5"));
		meldung.setVisibility(View.VISIBLE);
	}

	private void zeigeOk(String t) {
		meldung.setText(t);
		meldung.setTextColor(Color.parseColor("#BBF7D0"));
		meldung.setVisibility(View.VISIBLE);
	}

	private int dp(float v) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
	}

	private TextView text(float sp, int farbe, boolean fett) {
		TextView t = new TextView(this);
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
		t.setTextColor(farbe);
		t.setGravity(Gravity.CENTER);
		if (fett) t.setTypeface(Typeface.DEFAULT_BOLD);
		return t;
	}

	private Button knopf(String beschriftung, String farbe) {
		Button k = new Button(this);
		k.setText(beschriftung);
		k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
		k.setTextColor(Color.WHITE);
		k.setAllCaps(false);
		GradientDrawable g = new GradientDrawable();
		g.setColor(Color.parseColor(farbe));
		g.setCornerRadius(dp(14));
		k.setBackground(g);
		return k;
	}

	private View baue() {
		int hell = Color.parseColor("#E5E7EB"), grau = Color.parseColor("#94A3B8");
		LinearLayout l = new LinearLayout(this);
		l.setOrientation(LinearLayout.VERTICAL);
		l.setBackgroundColor(Color.parseColor("#0F172A"));
		// Eigener Abstand PLUS Systemleisten (fitsSystemWindows ersetzte den Abstand durch die
		// Systemraender — seitlich 0, Kasten und Knoepfe klebten am Rand).
		l.setOnApplyWindowInsetsListener((v, ins) -> {
			v.setPadding(dp(24) + ins.getSystemWindowInsetLeft(), dp(24) + ins.getSystemWindowInsetTop(),
					dp(24) + ins.getSystemWindowInsetRight(), dp(16) + ins.getSystemWindowInsetBottom());
			return ins;
		});
		inhalt = l;

		TextView titel = text(26, hell, true);
		titel.setText("Rosenweg Karten");
		l.addView(titel);

		hinweis = text(16, grau, false);
		hinweis.setText("Berechtigung wird geprüft …");
		hinweis.setPadding(0, dp(6), 0, dp(20));
		l.addView(hinweis);

		kartenFeld = new LinearLayout(this);
		kartenFeld.setOrientation(LinearLayout.VERTICAL);
		kartenFeld.setPadding(dp(18), dp(18), dp(18), dp(18));
		GradientDrawable rand = new GradientDrawable();
		rand.setColor(Color.parseColor("#1E293B"));
		rand.setCornerRadius(dp(18));
		rand.setStroke(dp(2), Color.parseColor("#D32F2F"));
		kartenFeld.setBackground(rand);
		inhaber = text(22, hell, true);
		nummer = text(16, hell, false);
		nummer.setPadding(0, dp(6), 0, 0);
		zustand = text(15, grau, false);
		zustand.setPadding(0, dp(8), 0, 0);
		uidText = text(12, grau, false);
		uidText.setTypeface(Typeface.MONOSPACE);
		uidText.setPadding(0, dp(10), 0, 0);
		kartenFeld.addView(inhaber);
		kartenFeld.addView(nummer);
		kartenFeld.addView(zustand);
		kartenFeld.addView(uidText);
		kartenFeld.setVisibility(View.GONE);
		l.addView(kartenFeld);

		meldung = text(15, Color.parseColor("#FCA5A5"), false);
		meldung.setPadding(0, dp(14), 0, 0);
		meldung.setVisibility(View.GONE);
		l.addView(meldung);

		View platz = new View(this);
		l.addView(platz, new LinearLayout.LayoutParams(1, 0, 1f));

		zuordnen = knopf("Einer Person zuordnen", "#2563EB");
		zuordnen.setOnClickListener(v -> { if (!beschaeftigt) personWaehlen(); });
		zuordnen.setVisibility(View.GONE);
		LinearLayout.LayoutParams zp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
		zp.bottomMargin = dp(10);
		l.addView(zuordnen, zp);

		entziehen = knopf("Entziehen", "#DC2626");
		entziehen.setOnClickListener(v -> { if (!beschaeftigt) entziehenFragen(); });
		entziehen.setVisibility(View.GONE);
		l.addView(entziehen, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

		loeschen = knopf("Aus dem Bestand löschen", "#7F1D1D");
		loeschen.setOnClickListener(v -> { if (!beschaeftigt) loeschenFragen(); });
		loeschen.setVisibility(View.GONE);
		l.addView(loeschen, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

		// Wer angemeldet ist; Abmelden in den Einstellungen wie in den anderen Rosenweg-Apps
		l.addView(einstellungenZeile(grau));
		return l;
	}

	private TextView einstellungenZeile(int farbe) {
		TextView ab = text(13, farbe, false);
		ab.setText(EinstellungenActivity.anmeldeText(this) + " · Einstellungen");
		ab.setPadding(0, dp(14), 0, dp(4));
		ab.setOnClickListener(x -> startActivity(new Intent(this, EinstellungenActivity.class)));
		return ab;
	}
}
